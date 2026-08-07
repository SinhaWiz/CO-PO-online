package org.example.copo.service;

import lombok.RequiredArgsConstructor;
import org.example.copo.entity.Assessment;
import org.example.copo.entity.AssessmentQuestion;
import org.example.copo.entity.AssessmentQuestionPO;
import org.example.copo.entity.CO;
import org.example.copo.entity.CourseAssessmentSection;
import org.example.copo.entity.Enrollment;
import org.example.copo.entity.PO;
import org.example.copo.entity.StudentAssessmentMarks;
import org.example.copo.repository.AssessmentQuestionPORepository;
import org.example.copo.repository.AssessmentQuestionRepository;
import org.example.copo.repository.AssessmentRepository;
import org.example.copo.repository.CORepository;
import org.example.copo.repository.CourseAssessmentSectionRepository;
import org.example.copo.repository.EnrollmentRepository;
import org.example.copo.repository.PORepository;
import org.example.copo.repository.StudentAssessmentMarksRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private final AssessmentQuestionPORepository questionPoRepository;
    private final StudentAssessmentMarksRepository marksRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CORepository coRepository;
    private final PORepository poRepository;
    private final CourseAssignmentThresholdService thresholdService;
    private final AssignmentAuthorizationService authorizationService;

    public record OutcomeAttainmentRow(String code, double attainedPercent) {}
    public record AttainmentResult(List<OutcomeAttainmentRow> rows, List<String> issues) {}

    // Per-student, per-CO obtained-marks matrix plus the same completeness gating
    // getCoAttainment uses. Pulled out so SummaryReportService (evaluation sheet +
    // attainment histogram, which need the raw per-student breakdown rather than just
    // the final aggregate percentage) can reuse the exact same gating and aggregation
    // instead of a third copy of it - the desktop app itself already had this logic
    // duplicated twice across its Summary Report screen alone.
    public record CoMatrix(List<String> studentIds, Map<String, Double> coTotal, Map<String, Map<String, Double>> studentCoObtained, List<String> issues) {}

    public CoMatrix buildCoMatrix(String courseCode, String programme, String academicYear) {
        List<CourseAssessmentSection> sections =
            sectionRepository.findByCourseCodeAndProgrammeOrderBySectionOrderAsc(courseCode, programme);
        if (sections.isEmpty()) {
            return blockedMatrix("No assessment sections configured for this course.");
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
            return new CoMatrix(List.of(), Map.of(), Map.of(), issues);
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
            return blockedMatrix("Questions exist but none have CO mappings.");
        }

        List<Enrollment> enrollments = enrollmentRepository.findByCourseIdAndProgrammeAndAcademicYear(courseCode, programme, academicYear);
        List<String> studentIds = enrollments.stream().map(Enrollment::getStudentId).distinct().toList();
        if (studentIds.isEmpty()) {
            return blockedMatrix("No students enrolled for this course in " + academicYear + ".");
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
            return blockedMatrix("No marks have been entered yet.");
        }
        if (graded < totalRequired) {
            return blockedMatrix("Not all required marks are graded yet (" + graded + " of " + totalRequired + ").");
        }

        return new CoMatrix(studentIds, coTotal, studentCoObtained, List.of());
    }

    private CoMatrix blockedMatrix(String issue) {
        return new CoMatrix(List.of(), Map.of(), Map.of(), List.of(issue));
    }

    public AttainmentResult getCoAttainment(
        String facultyEmail, String courseCode, String programme, String academicYear, String department
    ) {
        authorizationService.requireAssignedToCourse(facultyEmail, courseCode, programme, academicYear);

        CoMatrix matrix = buildCoMatrix(courseCode, programme, academicYear);
        if (!matrix.issues().isEmpty()) {
            return new AttainmentResult(List.of(), matrix.issues());
        }

        double threshold = thresholdService.getThresholds(courseCode, programme, academicYear, department).coIndividual() / 100.0;

        Map<String, Integer> attainedCounts = new HashMap<>();
        for (String co : matrix.coTotal().keySet()) attainedCounts.put(co, 0);

        for (String studentId : matrix.studentIds()) {
            Map<String, Double> gotMap = matrix.studentCoObtained().get(studentId);
            for (String co : matrix.coTotal().keySet()) {
                double denom = matrix.coTotal().get(co);
                if (denom <= 0) continue;
                double got = gotMap.getOrDefault(co, 0.0);
                if (got / denom >= threshold) {
                    attainedCounts.merge(co, 1, Integer::sum);
                }
            }
        }

        List<OutcomeAttainmentRow> rows = matrix.coTotal().keySet().stream()
            .sorted(Comparator.comparingInt(AttainmentService::extractNumber))
            .map(co -> new OutcomeAttainmentRow(co, attainedCounts.getOrDefault(co, 0) * 100.0 / matrix.studentIds().size()))
            .toList();

        return new AttainmentResult(rows, List.of());
    }

    public AttainmentResult getPoAttainment(
        String facultyEmail, String courseCode, String programme, String academicYear, String department
    ) {
        authorizationService.requireAssignedToCourse(facultyEmail, courseCode, programme, academicYear);

        List<CourseAssessmentSection> sections =
            sectionRepository.findByCourseCodeAndProgrammeOrderBySectionOrderAsc(courseCode, programme);
        if (sections.isEmpty()) {
            return blocked("No assessment sections configured for this course.");
        }

        Map<Integer, String> poNumberById = new HashMap<>();
        for (PO po : poRepository.findAll()) {
            poNumberById.put(po.getId(), po.getPoNumber());
        }

        List<String> issues = new ArrayList<>();
        List<AssessmentQuestion> allQuestions = new ArrayList<>();
        Map<Integer, List<Integer>> poIdsByQuestion = new HashMap<>();

        for (CourseAssessmentSection section : sections) {
            Optional<Assessment> assessment = assessmentRepository.findBySectionIdAndAcademicYear(section.getId(), academicYear);
            List<AssessmentQuestion> sectionQuestions = assessment
                .map(a -> questionRepository.findByAssessmentId(a.getId()))
                .orElse(List.of());

            if (sectionQuestions.isEmpty()) {
                issues.add("Section \"" + section.getDisplayName() + "\" has no questions defined for " + academicYear + ".");
                continue;
            }

            List<Integer> qIds = sectionQuestions.stream().map(AssessmentQuestion::getId).toList();
            Map<Integer, List<Integer>> sectionPoMap = questionPoRepository.findByQuestionIdIn(qIds).stream()
                .collect(Collectors.groupingBy(
                    AssessmentQuestionPO::getQuestionId,
                    Collectors.mapping(AssessmentQuestionPO::getPoId, Collectors.toList())
                ));

            boolean hasPoMapped = sectionQuestions.stream()
                .anyMatch(q -> !sectionPoMap.getOrDefault(q.getId(), List.of()).isEmpty());
            if (!hasPoMapped) {
                issues.add("Section \"" + section.getDisplayName() + "\" has questions but none mapped to a PO.");
                continue;
            }

            allQuestions.addAll(sectionQuestions);
            poIdsByQuestion.putAll(sectionPoMap);
        }

        if (!issues.isEmpty()) {
            return new AttainmentResult(List.of(), issues);
        }

        // A question can map to more than one PO - its marks count toward every PO
        // it's mapped to, both in the denominator and in a student's obtained total.
        Map<String, Double> poTotal = new HashMap<>();
        Map<Integer, List<String>> questionIdToPos = new HashMap<>();
        for (AssessmentQuestion q : allQuestions) {
            List<Integer> poIds = poIdsByQuestion.getOrDefault(q.getId(), List.of());
            if (poIds.isEmpty()) continue;

            List<String> poNumbers = new ArrayList<>();
            for (Integer poId : poIds) {
                String poNumber = poNumberById.get(poId);
                if (poNumber == null) continue;
                poTotal.merge(poNumber, q.getMarks(), Double::sum);
                poNumbers.add(poNumber);
            }
            if (!poNumbers.isEmpty()) questionIdToPos.put(q.getId(), poNumbers);
        }

        if (poTotal.isEmpty()) {
            return blocked("Questions exist but none have PO mappings.");
        }

        List<Enrollment> enrollments = enrollmentRepository.findByCourseIdAndProgrammeAndAcademicYear(courseCode, programme, academicYear);
        List<String> studentIds = enrollments.stream().map(Enrollment::getStudentId).distinct().toList();
        if (studentIds.isEmpty()) {
            return blocked("No students enrolled for this course in " + academicYear + ".");
        }

        List<Integer> questionIds = new ArrayList<>(questionIdToPos.keySet());
        Map<String, Double> obtainedByKey = new HashMap<>();
        for (StudentAssessmentMarks mark : marksRepository.findByQuestionIdIn(questionIds)) {
            obtainedByKey.put(mark.getStudentId() + "::" + mark.getQuestionId(), mark.getMarksObtained());
        }

        // totalRequired/graded are counted once per (student, question) - matching
        // POReportDialogController exactly - not once per (student, question, PO). A
        // question mapped to 3 POs is still one mark cell to grade, not three.
        int totalRequired = studentIds.size() * questionIdToPos.size();
        int graded = 0;
        Map<String, Map<String, Double>> studentPoObtained = new HashMap<>();
        for (String studentId : studentIds) {
            Map<String, Double> perPo = new HashMap<>();
            studentPoObtained.put(studentId, perPo);
            for (Map.Entry<Integer, List<String>> entry : questionIdToPos.entrySet()) {
                Double obtained = obtainedByKey.get(studentId + "::" + entry.getKey());
                if (obtained != null) {
                    graded++;
                    for (String po : entry.getValue()) {
                        perPo.merge(po, obtained, Double::sum);
                    }
                }
            }
        }

        if (graded == 0) {
            return blocked("No marks have been entered yet.");
        }
        if (graded < totalRequired) {
            return blocked("Not all required marks are graded yet (" + graded + " of " + totalRequired + ").");
        }

        double threshold = thresholdService.getThresholds(courseCode, programme, academicYear, department).poIndividual() / 100.0;

        Map<String, Integer> attainedCounts = new HashMap<>();
        for (String po : poTotal.keySet()) attainedCounts.put(po, 0);

        for (String studentId : studentIds) {
            Map<String, Double> gotMap = studentPoObtained.get(studentId);
            for (String po : poTotal.keySet()) {
                double denom = poTotal.get(po);
                if (denom <= 0) continue;
                double got = gotMap.getOrDefault(po, 0.0);
                if (got / denom >= threshold) {
                    attainedCounts.merge(po, 1, Integer::sum);
                }
            }
        }

        List<OutcomeAttainmentRow> rows = poTotal.keySet().stream()
            .sorted(Comparator.comparingInt(AttainmentService::extractNumber))
            .map(po -> new OutcomeAttainmentRow(po, attainedCounts.getOrDefault(po, 0) * 100.0 / studentIds.size()))
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
