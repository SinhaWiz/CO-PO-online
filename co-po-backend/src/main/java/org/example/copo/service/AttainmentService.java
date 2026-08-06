package org.example.copo.service;

import lombok.RequiredArgsConstructor;
import org.example.copo.entity.Assessment;
import org.example.copo.entity.AssessmentQuestion;
import org.example.copo.entity.CO;
import org.example.copo.entity.CourseAssessmentSection;
import org.example.copo.entity.Enrollment;
import org.example.copo.entity.StudentAssessmentMarks;
import org.example.copo.repository.AssessmentQuestionRepository;
import org.example.copo.repository.AssessmentRepository;
import org.example.copo.repository.CORepository;
import org.example.copo.repository.CourseAssessmentSectionRepository;
import org.example.copo.repository.EnrollmentRepository;
import org.example.copo.repository.StudentAssessmentMarksRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CO/PO attainment - the core of the whole system. Ported directly from the desktop
 * app's COReportDialogController/POReportDialogController rather than redesigned,
 * since these numbers are what actually gets reported for accreditation. Returns
 * structured blocking issues instead of throwing/showing an alert dialog, but the
 * underlying math and the completeness gating (every relevant cell must be graded)
 * are unchanged.
 */
@Service
@RequiredArgsConstructor
public class AttainmentService {

    private final CourseAssessmentSectionRepository sectionRepository;
    private final AssessmentRepository assessmentRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final StudentAssessmentMarksRepository marksRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CORepository coRepository;
    private final CourseAssignmentThresholdService thresholdService;
    private final AssignmentAuthorizationService authorizationService;

    public record OutcomeAttainmentRow(String code, double attainedPercent) {}
    public record AttainmentResult(List<OutcomeAttainmentRow> rows, List<String> issues) {}

    public AttainmentResult getCoAttainment(
        String facultyEmail, String courseCode, String programme, String academicYear, String department
    ) {
        authorizationService.requireAssignedToCourse(facultyEmail, courseCode, programme, academicYear);

        List<CourseAssessmentSection> sections =
            sectionRepository.findByCourseCodeAndProgrammeOrderBySectionOrderAsc(courseCode, programme);
        if (sections.isEmpty()) {
            return blocked("No assessment sections configured for this course.");
        }

        Map<Integer, String> coNumberById = new HashMap<>();
        for (CO co : coRepository.findAll()) {
            coNumberById.put(co.getId(), co.getCoNumber());
        }

        List<String> issues = new ArrayList<>();
        List<AssessmentQuestion> allQuestions = new ArrayList<>();

        for (CourseAssessmentSection section : sections) {
            Optional<Assessment> assessment = assessmentRepository.findBySectionIdAndAcademicYear(section.getId(), academicYear);
            List<AssessmentQuestion> sectionQuestions = assessment
                .map(a -> questionRepository.findByAssessmentId(a.getId()))
                .orElse(List.of());

            if (sectionQuestions.isEmpty()) {
                issues.add("Section \"" + section.getDisplayName() + "\" has no questions defined for " + academicYear + ".");
            } else if (sectionQuestions.stream().noneMatch(q -> q.getCoId() != null)) {
                issues.add("Section \"" + section.getDisplayName() + "\" has questions but none mapped to a CO.");
            } else {
                allQuestions.addAll(sectionQuestions);
            }
        }

        if (!issues.isEmpty()) {
            return new AttainmentResult(List.of(), issues);
        }

        Map<String, Double> coTotal = new HashMap<>();
        Map<Integer, String> questionIdToCo = new HashMap<>();
        for (AssessmentQuestion q : allQuestions) {
            if (q.getCoId() == null) continue;
            String co = coNumberById.get(q.getCoId());
            if (co == null) continue;
            coTotal.merge(co, q.getMarks(), Double::sum);
            questionIdToCo.put(q.getId(), co);
        }

        if (coTotal.isEmpty()) {
            return blocked("Questions exist but none have CO mappings.");
        }

        List<Enrollment> enrollments = enrollmentRepository.findByCourseIdAndProgrammeAndAcademicYear(courseCode, programme, academicYear);
        List<String> studentIds = enrollments.stream().map(Enrollment::getStudentId).distinct().toList();
        if (studentIds.isEmpty()) {
            return blocked("No students enrolled for this course in " + academicYear + ".");
        }

        List<Integer> questionIds = new ArrayList<>(questionIdToCo.keySet());
        Map<String, Double> obtainedByKey = new HashMap<>();
        for (StudentAssessmentMarks mark : marksRepository.findByQuestionIdIn(questionIds)) {
            obtainedByKey.put(mark.getStudentId() + "::" + mark.getQuestionId(), mark.getMarksObtained());
        }

        int totalRequired = studentIds.size() * questionIdToCo.size();
        int graded = 0;
        Map<String, Map<String, Double>> studentCoObtained = new HashMap<>();
        for (String studentId : studentIds) {
            Map<String, Double> perCo = new HashMap<>();
            studentCoObtained.put(studentId, perCo);
            for (Map.Entry<Integer, String> entry : questionIdToCo.entrySet()) {
                Double obtained = obtainedByKey.get(studentId + "::" + entry.getKey());
                if (obtained != null) {
                    graded++;
                    perCo.merge(entry.getValue(), obtained, Double::sum);
                }
            }
        }

        if (graded == 0) {
            return blocked("No marks have been entered yet.");
        }
        if (graded < totalRequired) {
            return blocked("Not all required marks are graded yet (" + graded + " of " + totalRequired + ").");
        }

        double threshold = thresholdService.getThresholds(courseCode, programme, academicYear, department).coIndividual() / 100.0;

        Map<String, Integer> attainedCounts = new HashMap<>();
        for (String co : coTotal.keySet()) attainedCounts.put(co, 0);

        for (String studentId : studentIds) {
            Map<String, Double> gotMap = studentCoObtained.get(studentId);
            for (String co : coTotal.keySet()) {
                double denom = coTotal.get(co);
                if (denom <= 0) continue;
                double got = gotMap.getOrDefault(co, 0.0);
                if (got / denom >= threshold) {
                    attainedCounts.merge(co, 1, Integer::sum);
                }
            }
        }

        List<OutcomeAttainmentRow> rows = coTotal.keySet().stream()
            .sorted(Comparator.comparingInt(AttainmentService::extractNumber))
            .map(co -> new OutcomeAttainmentRow(co, attainedCounts.getOrDefault(co, 0) * 100.0 / studentIds.size()))
            .toList();

        return new AttainmentResult(rows, List.of());
    }

    private AttainmentResult blocked(String issue) {
        return new AttainmentResult(List.of(), List.of(issue));
    }

    private static int extractNumber(String label) {
        try {
            return Integer.parseInt(label.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }
}
