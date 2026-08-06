package org.example.copo.service;

import lombok.RequiredArgsConstructor;
import org.example.copo.entity.Assessment;
import org.example.copo.entity.AssessmentQuestion;
import org.example.copo.entity.Course;
import org.example.copo.entity.CourseAssessmentSection;
import org.example.copo.entity.Enrollment;
import org.example.copo.entity.EnrollmentAttendance;
import org.example.copo.entity.Student;
import org.example.copo.entity.StudentAssessmentMarks;
import org.example.copo.repository.AssessmentQuestionRepository;
import org.example.copo.repository.AssessmentRepository;
import org.example.copo.repository.CourseAssessmentSectionRepository;
import org.example.copo.repository.CourseRepository;
import org.example.copo.repository.EnrollmentAttendanceRepository;
import org.example.copo.repository.EnrollmentRepository;
import org.example.copo.repository.StudentAssessmentMarksRepository;
import org.example.copo.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Weighted final course results, ported from the desktop app's ResultService. Theory
 * courses only (course code's last digit odd - a lab/non-theory course code ends even).
 * Two weighting schemes depending on the majority enrolled batch:
 *   - batch < 23 ("legacy"): 10% attendance (credits-based bucket conversion) + 15% best-3
 *     quiz/assignment + 25% mid + 50% final
 *   - batch >= 23: 20% best-3 quiz/assignment + 40% mid + 40% final, no attendance
 * Sections are matched to a category by case-insensitive substring on their display
 * name ("quiz"/"assignment" -> QUIZ_ASSIGNMENT, "mid" -> MID, "final" -> FINAL); anything
 * else is UNKNOWN and excluded from weighting, same as the source.
 */
@Service
@RequiredArgsConstructor
public class CourseResultService {

    private final CourseAssessmentSectionRepository sectionRepository;
    private final AssessmentRepository assessmentRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final StudentAssessmentMarksRepository marksRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentAttendanceRepository attendanceRepository;
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final AssignmentAuthorizationService authorizationService;

    private enum SectionCategory { QUIZ_ASSIGNMENT, MID, FINAL, UNKNOWN }

    public record StudentResultRow(
        String studentId, String studentName, int batch,
        double attendanceWeighted, double quizAssignmentWeighted, double midWeighted, double finalWeighted,
        double totalPercentage, String letterGrade, double gradePoint, boolean passed
    ) {}

    public record GradeDistributionRow(String letterGrade, int count, double percentage) {}

    public record ResultStatistics(
        int totalStudents, int passedCount, int failedCount,
        double passPercentage, double failPercentage, double averagePercentage, double averageGpa,
        List<GradeDistributionRow> gradeDistribution
    ) {}

    public record CourseResultData(
        int majorityBatch, boolean batchBelow23, List<StudentResultRow> results, ResultStatistics statistics
    ) {}

    public record ResultsOutcome(CourseResultData data, String issue) {}

    public ResultsOutcome getResults(
        String facultyEmail, String courseCode, String programme, String academicYear, String department
    ) {
        authorizationService.requireAssignedToCourse(facultyEmail, courseCode, programme, academicYear);

        if (!isTheoryCourse(courseCode)) {
            return new ResultsOutcome(null, "View Results is only available for theory courses (course code's last digit must be odd).");
        }

        List<Enrollment> enrollments = enrollmentRepository.findByCourseIdAndProgrammeAndAcademicYear(courseCode, programme, academicYear);
        List<String> studentIds = enrollments.stream().map(Enrollment::getStudentId).distinct().toList();
        if (studentIds.isEmpty()) {
            return new ResultsOutcome(new CourseResultData(0, false, List.of(), emptyStatistics()), null);
        }

        Map<String, Student> studentsById = studentRepository.findAllById(studentIds).stream()
            .collect(Collectors.toMap(Student::getId, s -> s));

        int majorityBatch = computeMajorityBatch(studentsById.values());
        boolean below23 = majorityBatch > 0 && majorityBatch < 23;

        Course course = courseRepository.findById(new Course.CourseId(courseCode, programme)).orElse(null);
        double courseCredits = course != null && course.getCredits() != null ? course.getCredits() : 0.0;

        List<CourseAssessmentSection> sections = sectionRepository.findByCourseCodeAndProgrammeOrderBySectionOrderAsc(courseCode, programme);
        Map<SectionCategory, List<CourseAssessmentSection>> categorised = classifySections(sections);

        Map<String, Double> attendanceByStudent = new HashMap<>();
        if (below23) {
            if (courseCredits <= 0.0) {
                return new ResultsOutcome(null, "Course credits are required to calculate attendance-based results.");
            }

            Map<String, Double> recorded = attendanceRepository
                .findByCourseIdAndProgrammeAndAcademicYear(courseCode, programme, academicYear).stream()
                .filter(a -> a.getAttendancePercentage() != null)
                .collect(Collectors.toMap(EnrollmentAttendance::getStudentId, EnrollmentAttendance::getAttendancePercentage));

            boolean missingAny = studentIds.stream().anyMatch(id -> !recorded.containsKey(id));
            if (missingAny) {
                return new ResultsOutcome(
                    null,
                    "Attendance percentages are not entered for all enrolled students. Please complete the Attendance tab before viewing results."
                );
            }
            attendanceByStudent.putAll(recorded);
        }

        // Per-student per-section percentage: obtained / section's total max marks * 100.
        Map<String, Map<Integer, Double>> studentSectionPct = new HashMap<>();
        for (CourseAssessmentSection section : sections) {
            Optional<Assessment> assessment = assessmentRepository.findBySectionIdAndAcademicYear(section.getId(), academicYear);
            List<AssessmentQuestion> questions = assessment.map(a -> questionRepository.findByAssessmentId(a.getId())).orElse(List.of());
            double totalMax = questions.stream().mapToDouble(AssessmentQuestion::getMarks).sum();

            Map<String, Double> studentObtained = new HashMap<>();
            List<Integer> questionIds = questions.stream().map(AssessmentQuestion::getId).toList();
            if (!questionIds.isEmpty()) {
                for (StudentAssessmentMarks mark : marksRepository.findByQuestionIdIn(questionIds)) {
                    studentObtained.merge(mark.getStudentId(), mark.getMarksObtained(), Double::sum);
                }
            }

            for (String studentId : studentIds) {
                double obtained = studentObtained.getOrDefault(studentId, 0.0);
                double pct = totalMax > 0 ? (obtained / totalMax) * 100.0 : 0.0;
                studentSectionPct.computeIfAbsent(studentId, k -> new HashMap<>()).put(section.getId(), pct);
            }
        }

        List<StudentResultRow> rows = new ArrayList<>();
        for (String studentId : studentIds) {
            Map<Integer, Double> pctMap = studentSectionPct.getOrDefault(studentId, Map.of());

            double midPct = averageSections(categorised.getOrDefault(SectionCategory.MID, List.of()), pctMap);
            double finalPct = averageSections(categorised.getOrDefault(SectionCategory.FINAL, List.of()), pctMap);
            double quizAssignPct = bestOfN(categorised.getOrDefault(SectionCategory.QUIZ_ASSIGNMENT, List.of()), pctMap, 3);

            double attW;
            double qaW;
            double midW;
            double finW;
            if (below23) {
                double attendancePct = attendanceByStudent.getOrDefault(studentId, 0.0);
                double rawAttendanceMarks = calculateAttendanceRawMarks(attendancePct, courseCredits);
                attW = normalizeAttendanceContribution(rawAttendanceMarks, courseCredits);
                qaW = quizAssignPct * 0.15;
                midW = midPct * 0.25;
                finW = finalPct * 0.50;
            } else {
                attW = 0.0;
                qaW = quizAssignPct * 0.20;
                midW = midPct * 0.40;
                finW = finalPct * 0.40;
            }
            double weighted = attW + qaW + midW + finW;

            String letter = GradeProcessor.getLetterGrade(weighted);
            double gp = GradeProcessor.getGradePoint(letter);
            boolean pass = GradeProcessor.isPassing(weighted);

            Student student = studentsById.get(studentId);
            rows.add(new StudentResultRow(
                studentId,
                student != null ? student.getName() : studentId,
                student != null && student.getBatch() != null ? student.getBatch() : 0,
                attW, qaW, midW, finW, weighted, letter, gp, pass
            ));
        }

        return new ResultsOutcome(new CourseResultData(majorityBatch, below23, rows, computeStatistics(rows)), null);
    }

    public static boolean isTheoryCourse(String courseCode) {
        if (courseCode == null || courseCode.isEmpty()) return false;
        char lastChar = courseCode.charAt(courseCode.length() - 1);
        if (!Character.isDigit(lastChar)) return false;
        int lastDigit = Character.getNumericValue(lastChar);
        return lastDigit % 2 != 0;
    }

    public static double calculateAttendanceMultiplier(double attendancePercentage) {
        if (attendancePercentage >= 95.0) return 10.0;
        if (attendancePercentage >= 90.0) return 8.0;
        if (attendancePercentage >= 85.0) return 6.0;
        if (attendancePercentage >= 80.0) return 4.0;
        if (attendancePercentage >= 75.0) return 2.0;
        return 0.0;
    }

    public static double calculateAttendanceRawMarks(double attendancePercentage, double courseCredits) {
        if (courseCredits <= 0.0) return 0.0;
        return courseCredits * calculateAttendanceMultiplier(attendancePercentage);
    }

    public static double normalizeAttendanceContribution(double rawAttendanceMarks, double courseCredits) {
        if (courseCredits <= 0.0) return 0.0;
        return rawAttendanceMarks / courseCredits;
    }

    private Map<SectionCategory, List<CourseAssessmentSection>> classifySections(List<CourseAssessmentSection> sections) {
        Map<SectionCategory, List<CourseAssessmentSection>> map = new HashMap<>();
        for (SectionCategory category : SectionCategory.values()) {
            map.put(category, new ArrayList<>());
        }

        for (CourseAssessmentSection section : sections) {
            String name = section.getDisplayName().toLowerCase();
            if (name.contains("quiz") || name.contains("assignment")) {
                map.get(SectionCategory.QUIZ_ASSIGNMENT).add(section);
            } else if (name.contains("mid")) {
                map.get(SectionCategory.MID).add(section);
            } else if (name.contains("final")) {
                map.get(SectionCategory.FINAL).add(section);
            } else {
                map.get(SectionCategory.UNKNOWN).add(section);
            }
        }
        return map;
    }

    // Averages the percentage across every section in the category (e.g. two Mid sections).
    private double averageSections(List<CourseAssessmentSection> sections, Map<Integer, Double> pctMap) {
        if (sections.isEmpty()) return 0.0;
        double sum = 0;
        for (CourseAssessmentSection section : sections) {
            sum += pctMap.getOrDefault(section.getId(), 0.0);
        }
        return sum / sections.size();
    }

    // Best n section percentages, averaged (still a 0-100 percentage, ready to be scaled by a weight).
    private double bestOfN(List<CourseAssessmentSection> sections, Map<Integer, Double> pctMap, int n) {
        if (sections.isEmpty()) return 0.0;

        List<Double> percentages = new ArrayList<>();
        for (CourseAssessmentSection section : sections) {
            percentages.add(pctMap.getOrDefault(section.getId(), 0.0));
        }
        percentages.sort(Comparator.reverseOrder());

        int take = Math.min(n, percentages.size());
        double sum = 0;
        for (int i = 0; i < take; i++) {
            sum += percentages.get(i);
        }
        return sum / take;
    }

    // Mode of enrolled students' batch numbers, ties broken toward the lower batch -
    // same rule Phase 3.3's attendance legacy-check uses, kept independent here since
    // this needs a Student collection rather than the enrollment/course args that
    // AttendanceService.computeMajorityBatch takes.
    private int computeMajorityBatch(Collection<Student> students) {
        if (students.isEmpty()) return 0;

        Map<Integer, Integer> counts = new HashMap<>();
        for (Student student : students) {
            if (student.getBatch() != null) {
                counts.merge(student.getBatch(), 1, Integer::sum);
            }
        }

        int majorityBatch = 0;
        int highestCount = -1;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            int batch = entry.getKey();
            int count = entry.getValue();
            if (count > highestCount || (count == highestCount && (majorityBatch == 0 || batch < majorityBatch))) {
                majorityBatch = batch;
                highestCount = count;
            }
        }
        return majorityBatch;
    }

    private ResultStatistics computeStatistics(List<StudentResultRow> rows) {
        if (rows.isEmpty()) {
            return emptyStatistics();
        }

        int total = rows.size();
        int passed = (int) rows.stream().filter(StudentResultRow::passed).count();
        int failed = total - passed;
        double sumPercentage = rows.stream().mapToDouble(StudentResultRow::totalPercentage).sum();
        double sumGpa = rows.stream().mapToDouble(StudentResultRow::gradePoint).sum();

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String letter : GradeProcessor.getAllLetterGrades()) {
            counts.put(letter, 0);
        }
        for (StudentResultRow row : rows) {
            counts.merge(row.letterGrade(), 1, Integer::sum);
        }

        List<GradeDistributionRow> distribution = counts.entrySet().stream()
            .map(e -> new GradeDistributionRow(e.getKey(), e.getValue(), e.getValue() * 100.0 / total))
            .toList();

        return new ResultStatistics(
            total, passed, failed,
            passed * 100.0 / total, failed * 100.0 / total,
            sumPercentage / total, sumGpa / total,
            distribution
        );
    }

    private ResultStatistics emptyStatistics() {
        List<GradeDistributionRow> distribution = GradeProcessor.getAllLetterGrades().stream()
            .map(letter -> new GradeDistributionRow(letter, 0, 0.0))
            .toList();
        return new ResultStatistics(0, 0, 0, 0.0, 0.0, 0.0, 0.0, distribution);
    }
}
