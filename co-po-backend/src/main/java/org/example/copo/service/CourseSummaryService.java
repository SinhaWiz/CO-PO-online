package org.example.copo.service;

import lombok.RequiredArgsConstructor;
import org.example.copo.entity.Assessment;
import org.example.copo.entity.AssessmentQuestion;
import org.example.copo.entity.Course;
import org.example.copo.entity.CourseAssessmentSection;
import org.example.copo.entity.Enrollment;
import org.example.copo.repository.AssessmentQuestionRepository;
import org.example.copo.repository.AssessmentRepository;
import org.example.copo.repository.CourseAssessmentSectionRepository;
import org.example.copo.repository.CourseRepository;
import org.example.copo.repository.EnrollmentRepository;
import org.example.copo.repository.StudentAssessmentMarksRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ports the desktop app's Course Summary screen (CourseSummaryController) - a
 * setup/grading-completeness dashboard for one course assignment: one card per
 * assessment section showing its question count, total marks, and a status (Not
 * Setup / No Marks / X% Done / Complete), plus overall totals. Purely a read-only
 * status view of data that already exists elsewhere - no new persistence here.
 */
@Service
@RequiredArgsConstructor
public class CourseSummaryService {

    private final CourseRepository courseRepository;
    private final CourseAssessmentSectionRepository sectionRepository;
    private final AssessmentRepository assessmentRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentAssessmentMarksRepository marksRepository;
    private final AssignmentAuthorizationService authorizationService;

    public record AssessmentSectionSummary(
        String sectionName, int questionCount, double totalMarks, int marksEntered, int totalPossibleEntries
    ) {}

    public record CourseSummaryDto(
        String courseCode, String courseName, String programme, String academicYear,
        int enrolledStudents, List<AssessmentSectionSummary> sections,
        int totalQuestions, double totalMarks, double completionPercentage
    ) {}

    public CourseSummaryDto getCourseSummary(String facultyEmail, String courseCode, String programme, String academicYear) {
        authorizationService.requireAssignedToCourse(facultyEmail, courseCode, programme, academicYear);

        Course course = courseRepository.findById(new Course.CourseId(courseCode, programme)).orElse(null);
        List<CourseAssessmentSection> sections = sectionRepository.findByCourseCodeAndProgrammeOrderBySectionOrderAsc(courseCode, programme);

        List<Enrollment> enrollments = enrollmentRepository.findByCourseIdAndProgrammeAndAcademicYear(courseCode, programme, academicYear);
        int studentCount = (int) enrollments.stream().map(Enrollment::getStudentId).distinct().count();

        List<AssessmentSectionSummary> sectionSummaries = new ArrayList<>();
        int totalQuestions = 0;
        double totalMarksSum = 0;
        int totalEntered = 0, totalPossible = 0;

        for (CourseAssessmentSection section : sections) {
            Optional<Assessment> assessment = assessmentRepository.findBySectionIdAndAcademicYear(section.getId(), academicYear);
            List<AssessmentQuestion> questions = assessment.map(a -> questionRepository.findByAssessmentId(a.getId())).orElse(List.of());

            double sectionMarks = questions.stream().mapToDouble(AssessmentQuestion::getMarks).sum();
            int possible = questions.size() * studentCount;
            int entered = 0;
            if (!questions.isEmpty() && studentCount > 0) {
                List<Integer> questionIds = questions.stream().map(AssessmentQuestion::getId).toList();
                entered = (int) marksRepository.findByQuestionIdIn(questionIds).stream()
                    .filter(m -> m.getMarksObtained() != null)
                    .count();
            }

            sectionSummaries.add(new AssessmentSectionSummary(section.getDisplayName(), questions.size(), sectionMarks, entered, possible));
            totalQuestions += questions.size();
            totalMarksSum += sectionMarks;
            totalEntered += entered;
            totalPossible += possible;
        }

        double completionPercentage = totalPossible > 0 ? (totalEntered * 100.0 / totalPossible) : 0.0;

        return new CourseSummaryDto(
            courseCode, course != null ? course.getCourseName() : courseCode, programme, academicYear,
            studentCount, sectionSummaries, totalQuestions, totalMarksSum, completionPercentage
        );
    }
}
