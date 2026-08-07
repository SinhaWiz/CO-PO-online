package org.example.copo.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.copo.entity.Assessment;
import org.example.copo.entity.AssessmentQuestion;
import org.example.copo.entity.AssessmentQuestionPO;
import org.example.copo.entity.Course;
import org.example.copo.entity.CourseAssessmentSection;
import org.example.copo.entity.Enrollment;
import org.example.copo.entity.Student;
import org.example.copo.entity.StudentAssessmentMarks;
import org.example.copo.repository.AssessmentQuestionPORepository;
import org.example.copo.repository.AssessmentQuestionRepository;
import org.example.copo.repository.AssessmentRepository;
import org.example.copo.repository.CORepository;
import org.example.copo.repository.CourseAssessmentSectionRepository;
import org.example.copo.repository.CourseRepository;
import org.example.copo.repository.EnrollmentRepository;
import org.example.copo.repository.PORepository;
import org.example.copo.repository.StudentAssessmentMarksRepository;
import org.example.copo.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Ports the desktop app's Detailed Marks Report (PDF, DetailedMarksPDFGenerator) and
 * Consolidated Marks Report (Excel, ConsolidatedMarksExcelGenerator). Both are
 * read-only computed dumps - no faculty free text anywhere, unlike Course Report or
 * Summary Report - so the natural web UI is a section picker + Generate button, not an
 * on-screen editable form, matching desktop's own dialogs (which are pickers too, not
 * previews).
 *
 * Only CO attainment appears in the Detailed Marks PDF (desktop fetches a PO column
 * per question but never uses it - confirmed dead data, not ported). The Consolidated
 * Marks Excel workbook does have both a CO and a PO sheet.
 *
 * The two reports faithfully keep desktop's different gating policies rather than
 * being aligned to match: Detailed Marks requires every selected section to have
 * questions, students to be enrolled, and 100% of marks graded (same completeness bar
 * as the CO/PO reports, but - like desktop - without requiring CO/PO mapping, since
 * this is a diagnostic dump rather than an accreditation number). Consolidated Marks
 * has no gating at all beyond "select at least one section" - missing/ungraded data
 * just renders as "-", which is desktop's own choice and a reasonable one for a
 * working document whose job is partly to reveal gaps.
 */
@Service
@RequiredArgsConstructor
public class MarksReportService {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // Desktop's own display order: Quiz, Assignment, Lab, Project, Other, Mid, Final.
    private static final List<String> TYPE_ORDER = List.of("Quiz", "Assignment", "Lab", "Project", "Other", "Mid", "Final");
    private static final List<String> DISTRIBUTION_BANDS = List.of(
        "Below 40", "40-45", "45-50", "50-55", "55-60", "60-65", "65-70", "70-75", "75-80", "80-85", "85-90", "90-95", "95-100"
    );

    private final AssignmentAuthorizationService authorizationService;
    private final CourseAssessmentSectionRepository sectionRepository;
    private final AssessmentRepository assessmentRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final AssessmentQuestionPORepository questionPoRepository;
    private final StudentAssessmentMarksRepository marksRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final CORepository coRepository;
    private final PORepository poRepository;
    private final CourseAssignmentThresholdService thresholdService;

    public record QuestionRow(Integer questionId, String title, double maxMarks, String coCode) {}
    public record StudentRowMarks(String studentId, String studentName, Map<Integer, Double> marksByQuestionId, double total) {}
    public record SectionMarksTable(String sectionName, List<QuestionRow> questions, List<StudentRowMarks> students) {}
    public record CoAttainmentTableRow(String code, double maxMarks, int studentsAboveThreshold, int totalStudents, double percent, boolean attained) {}
    public record SectionReport(String sectionName, SectionMarksTable marksTable, List<CoAttainmentTableRow> coAttainment) {}
    public record ConsolidatedRow(String studentId, String studentName, Map<String, Double> percentByTypeCode, Map<String, Double> totalPercentByCode, double grandTotalPercent) {}
    public record TypeAggregate(List<String> types, List<String> codes, Map<String, Double> maxByTypeCode, List<ConsolidatedRow> rows) {}
    public record CoDistributionRow(String code, List<Integer> bandCounts) {}
    public record GenerateResult(String fileName, List<String> issues) {}

    // ============================== Detailed Marks Report (PDF) ==============================

    public GenerateResult generateDetailedMarksReport(
        String facultyEmail, String courseCode, String programme, String academicYear, String department,
        List<Integer> sectionIds, boolean includeOverallCoAttainment
    ) {
        authorizationService.requireAssignedToCourse(facultyEmail, courseCode, programme, academicYear);

        List<CourseAssessmentSection> sections = resolveSections(courseCode, programme, sectionIds);
        if (sections.isEmpty()) {
            return new GenerateResult(null, List.of("Select at least one assessment section."));
        }

        Map<Integer, List<AssessmentQuestion>> questionsBySection = new LinkedHashMap<>();
        List<String> issues = new ArrayList<>();
        for (CourseAssessmentSection section : sections) {
            List<AssessmentQuestion> questions = questionsForSection(section, academicYear);
            if (questions.isEmpty()) {
                issues.add("Section \"" + section.getDisplayName() + "\" has no questions defined for " + academicYear + ".");
            }
            questionsBySection.put(section.getId(), questions);
        }
        if (!issues.isEmpty()) {
            return new GenerateResult(null, issues);
        }

        List<String> studentIds = enrolledStudentIds(courseCode, programme, academicYear);
        if (studentIds.isEmpty()) {
            return new GenerateResult(null, List.of("No students enrolled for this course in " + academicYear + "."));
        }

        List<AssessmentQuestion> allQuestions = questionsBySection.values().stream().flatMap(List::stream).toList();
        List<Integer> allQuestionIds = allQuestions.stream().map(AssessmentQuestion::getId).toList();
        Map<String, Double> obtainedByKey = obtainedMarksByKey(allQuestionIds);

        int totalRequired = studentIds.size() * allQuestionIds.size();
        int graded = 0;
        for (String studentId : studentIds) {
            for (Integer qId : allQuestionIds) {
                if (obtainedByKey.get(studentId + "::" + qId) != null) graded++;
            }
        }
        if (graded < totalRequired) {
            return new GenerateResult(null, List.of("Not all required marks are graded yet (" + graded + " of " + totalRequired + ")."));
        }

        Map<String, String> studentNames = studentNames(studentIds);
        Map<Integer, String> coNumberById = loadCoNumbers();
        CourseAssignmentThresholdService.ThresholdsDto thresholds = thresholdService.getThresholds(courseCode, programme, academicYear, department);
        Course course = courseRepository.findById(new Course.CourseId(courseCode, programme)).orElse(null);

        List<SectionReport> sectionReports = new ArrayList<>();
        for (CourseAssessmentSection section : sections) {
            List<AssessmentQuestion> questions = questionsBySection.get(section.getId());
            SectionMarksTable marksTable = buildSectionMarksTable(section.getDisplayName(), questions, studentIds, studentNames, obtainedByKey);
            List<CoAttainmentTableRow> coAttainment = computeCoAttainment(
                questions, studentIds, obtainedByKey, coNumberById, thresholds.coIndividual(), thresholds.coCohort()
            );
            sectionReports.add(new SectionReport(section.getDisplayName(), marksTable, coAttainment));
        }

        TypeAggregate consolidated = buildCoTypeAggregate(sections, questionsBySection, studentIds, studentNames, obtainedByKey, coNumberById);
        List<CoDistributionRow> distribution = buildCoDistribution(consolidated.codes(), consolidated.rows());

        List<CoAttainmentTableRow> overallCoAttainment = includeOverallCoAttainment
            ? computeCoAttainment(allQuestions, studentIds, obtainedByKey, coNumberById, thresholds.coIndividual(), thresholds.coCohort())
            : List.of();

        try {
            String fileName = writeDetailedMarksPdf(
                courseCode, programme, academicYear, department, course,
                sectionReports, consolidated, distribution, includeOverallCoAttainment, overallCoAttainment
            );
            return new GenerateResult(fileName, List.of());
        } catch (Exception ex) {
            return new GenerateResult(null, List.of("Failed to save PDF: " + ex.getMessage()));
        }
    }

    // ============================== Consolidated Marks Report (Excel) ==============================

    public GenerateResult generateConsolidatedMarksReport(
        String facultyEmail, String courseCode, String programme, String academicYear, String department,
        List<Integer> sectionIds
    ) {
        authorizationService.requireAssignedToCourse(facultyEmail, courseCode, programme, academicYear);

        List<CourseAssessmentSection> sections = resolveSections(courseCode, programme, sectionIds);
        if (sections.isEmpty()) {
            return new GenerateResult(null, List.of("Select at least one assessment section."));
        }

        Map<Integer, List<AssessmentQuestion>> questionsBySection = new LinkedHashMap<>();
        for (CourseAssessmentSection section : sections) {
            questionsBySection.put(section.getId(), questionsForSection(section, academicYear));
        }

        List<String> studentIds = enrolledStudentIds(courseCode, programme, academicYear);
        Map<String, String> studentNames = studentNames(studentIds);

        List<AssessmentQuestion> allQuestions = questionsBySection.values().stream().flatMap(List::stream).toList();
        List<Integer> allQuestionIds = allQuestions.stream().map(AssessmentQuestion::getId).toList();
        Map<String, Double> obtainedByKey = obtainedMarksByKey(allQuestionIds);

        Map<Integer, String> coNumberById = loadCoNumbers();
        Map<Integer, String> poNumberById = loadPoNumbers();

        TypeAggregate coAggregate = buildCoTypeAggregate(sections, questionsBySection, studentIds, studentNames, obtainedByKey, coNumberById);
        TypeAggregate poAggregate = buildPoTypeAggregate(sections, questionsBySection, studentIds, studentNames, obtainedByKey, poNumberById);

        Course course = courseRepository.findById(new Course.CourseId(courseCode, programme)).orElse(null);

        try {
            String fileName = writeConsolidatedMarksExcel(courseCode, programme, academicYear, department, course, coAggregate, poAggregate);
            return new GenerateResult(fileName, List.of());
        } catch (Exception ex) {
            return new GenerateResult(null, List.of("Failed to save workbook: " + ex.getMessage()));
        }
    }

    // ============================== Shared data helpers ==============================

    private List<CourseAssessmentSection> resolveSections(String courseCode, String programme, List<Integer> sectionIds) {
        if (sectionIds == null || sectionIds.isEmpty()) return List.of();
        Set<Integer> requested = Set.copyOf(sectionIds);
        return sectionRepository.findByCourseCodeAndProgrammeOrderBySectionOrderAsc(courseCode, programme).stream()
            .filter(s -> requested.contains(s.getId()))
            .toList();
    }

    private List<AssessmentQuestion> questionsForSection(CourseAssessmentSection section, String academicYear) {
        Optional<Assessment> assessment = assessmentRepository.findBySectionIdAndAcademicYear(section.getId(), academicYear);
        return assessment.map(a -> questionRepository.findByAssessmentId(a.getId())).orElse(List.of());
    }

    private List<String> enrolledStudentIds(String courseCode, String programme, String academicYear) {
        List<Enrollment> enrollments = enrollmentRepository.findByCourseIdAndProgrammeAndAcademicYear(courseCode, programme, academicYear);
        return enrollments.stream().map(Enrollment::getStudentId).distinct().toList();
    }

    private Map<String, String> studentNames(List<String> studentIds) {
        Map<String, String> names = new HashMap<>();
        for (Student s : studentRepository.findAllById(studentIds)) names.put(s.getId(), s.getName());
        return names;
    }

    private Map<String, Double> obtainedMarksByKey(List<Integer> questionIds) {
        Map<String, Double> obtainedByKey = new HashMap<>();
        for (StudentAssessmentMarks mark : marksRepository.findByQuestionIdIn(questionIds)) {
            obtainedByKey.put(mark.getStudentId() + "::" + mark.getQuestionId(), mark.getMarksObtained());
        }
        return obtainedByKey;
    }

    private Map<Integer, String> loadCoNumbers() {
        Map<Integer, String> map = new HashMap<>();
        coRepository.findAll().forEach(co -> map.put(co.getId(), co.getCoNumber()));
        return map;
    }

    private Map<Integer, String> loadPoNumbers() {
        Map<Integer, String> map = new HashMap<>();
        poRepository.findAll().forEach(po -> map.put(po.getId(), po.getPoNumber()));
        return map;
    }

    private SectionMarksTable buildSectionMarksTable(
        String sectionName, List<AssessmentQuestion> questions, List<String> studentIds,
        Map<String, String> studentNames, Map<String, Double> obtainedByKey
    ) {
        Map<Integer, String> coNumberById = loadCoNumbers();
        List<QuestionRow> questionRows = questions.stream()
            .map(q -> new QuestionRow(q.getId(), q.getTitle(), q.getMarks(), q.getCoId() != null ? coNumberById.get(q.getCoId()) : null))
            .toList();

        List<StudentRowMarks> studentRows = new ArrayList<>();
        for (String studentId : studentIds) {
            Map<Integer, Double> marks = new HashMap<>();
            double total = 0;
            for (AssessmentQuestion q : questions) {
                Double obtained = obtainedByKey.get(studentId + "::" + q.getId());
                if (obtained != null) {
                    marks.put(q.getId(), obtained);
                    total += obtained;
                }
            }
            studentRows.add(new StudentRowMarks(studentId, studentNames.getOrDefault(studentId, studentId), marks, total));
        }
        studentRows.sort(Comparator.comparing(StudentRowMarks::studentName));

        return new SectionMarksTable(sectionName, questionRows, studentRows);
    }

    // Reused for both a single section's CO attainment table and the "Overall" one -
    // same formula AttainmentService.getCoAttainment uses, just callable against any
    // question subset instead of only "every question in the course".
    private List<CoAttainmentTableRow> computeCoAttainment(
        List<AssessmentQuestion> questions, List<String> studentIds, Map<String, Double> obtainedByKey,
        Map<Integer, String> coNumberById, double individualThresholdPct, double cohortThresholdPct
    ) {
        Map<String, Double> coTotal = new HashMap<>();
        Map<Integer, String> questionIdToCo = new HashMap<>();
        for (AssessmentQuestion q : questions) {
            if (q.getCoId() == null) continue;
            String co = coNumberById.get(q.getCoId());
            if (co == null) continue;
            coTotal.merge(co, q.getMarks(), Double::sum);
            questionIdToCo.put(q.getId(), co);
        }
        if (coTotal.isEmpty()) return List.of();

        Map<String, Map<String, Double>> studentCoObtained = new HashMap<>();
        for (String studentId : studentIds) {
            Map<String, Double> perCo = new HashMap<>();
            studentCoObtained.put(studentId, perCo);
            for (Map.Entry<Integer, String> entry : questionIdToCo.entrySet()) {
                Double obtained = obtainedByKey.get(studentId + "::" + entry.getKey());
                if (obtained != null) {
                    perCo.merge(entry.getValue(), obtained, Double::sum);
                }
            }
        }

        double individualFrac = individualThresholdPct / 100.0;
        List<CoAttainmentTableRow> rows = new ArrayList<>();
        for (String co : coTotal.keySet().stream().sorted(Comparator.comparingInt(MarksReportService::extractNumber)).toList()) {
            double max = coTotal.get(co);
            int above = 0;
            for (String studentId : studentIds) {
                double got = studentCoObtained.get(studentId).getOrDefault(co, 0.0);
                if (max > 0 && (got / max) >= individualFrac) above++;
            }
            double percent = studentIds.isEmpty() ? 0.0 : above * 100.0 / studentIds.size();
            rows.add(new CoAttainmentTableRow(co, max, above, studentIds.size(), percent, percent >= cohortThresholdPct));
        }
        return rows;
    }

    // Groups selected sections by assessment type (Quiz/Assignment/Lab/Project/Other/
    // Mid/Final, by a substring match on the section's display name) and computes, per
    // student, the obtained/max percentage for each (type, CO) pair plus a Total-per-CO
    // and Grand Total across types. Tolerant of missing marks (treated as 0, not
    // blocked) - callers that need a 100%-graded guarantee check that separately first.
    private TypeAggregate buildCoTypeAggregate(
        List<CourseAssessmentSection> sections, Map<Integer, List<AssessmentQuestion>> questionsBySection,
        List<String> studentIds, Map<String, String> studentNames, Map<String, Double> obtainedByKey, Map<Integer, String> coNumberById
    ) {
        Map<String, List<CourseAssessmentSection>> byType = groupSectionsByType(sections);
        List<String> presentTypes = TYPE_ORDER.stream().filter(t -> !byType.get(t).isEmpty()).toList();

        Set<String> codeSet = new TreeSet<>(Comparator.comparingInt(MarksReportService::extractNumber));
        Map<String, Double> maxByTypeCode = new HashMap<>();
        Map<Integer, String> questionIdToCode = new HashMap<>();
        Map<Integer, String> questionIdToType = new HashMap<>();

        for (String type : presentTypes) {
            for (CourseAssessmentSection section : byType.get(type)) {
                for (AssessmentQuestion q : questionsBySection.getOrDefault(section.getId(), List.of())) {
                    if (q.getCoId() == null) continue;
                    String co = coNumberById.get(q.getCoId());
                    if (co == null) continue;
                    questionIdToCode.put(q.getId(), co);
                    questionIdToType.put(q.getId(), type);
                    codeSet.add(co);
                    maxByTypeCode.merge(type + "::" + co, q.getMarks(), Double::sum);
                }
            }
        }

        List<ConsolidatedRow> rows = buildConsolidatedRows(studentIds, studentNames, obtainedByKey, presentTypes, codeSet, maxByTypeCode, questionIdToCode, questionIdToType);
        return new TypeAggregate(presentTypes, List.copyOf(codeSet), maxByTypeCode, rows);
    }

    // Same idea as buildCoTypeAggregate, but a question can map to more than one PO, so
    // its marks contribute to every PO it's mapped to (both denominator and obtained).
    private TypeAggregate buildPoTypeAggregate(
        List<CourseAssessmentSection> sections, Map<Integer, List<AssessmentQuestion>> questionsBySection,
        List<String> studentIds, Map<String, String> studentNames, Map<String, Double> obtainedByKey, Map<Integer, String> poNumberById
    ) {
        Map<String, List<CourseAssessmentSection>> byType = groupSectionsByType(sections);
        List<String> presentTypes = TYPE_ORDER.stream().filter(t -> !byType.get(t).isEmpty()).toList();

        Set<String> codeSet = new TreeSet<>(Comparator.comparingInt(MarksReportService::extractNumber));
        Map<String, Double> maxByTypeCode = new HashMap<>();
        Map<Integer, List<String>> questionIdToCodes = new HashMap<>();
        Map<Integer, String> questionIdToType = new HashMap<>();

        for (String type : presentTypes) {
            for (CourseAssessmentSection section : byType.get(type)) {
                List<AssessmentQuestion> questions = questionsBySection.getOrDefault(section.getId(), List.of());
                if (questions.isEmpty()) continue;
                List<Integer> qIds = questions.stream().map(AssessmentQuestion::getId).toList();
                Map<Integer, List<Integer>> poIdsByQuestion = questionPoRepository.findByQuestionIdIn(qIds).stream()
                    .collect(Collectors.groupingBy(AssessmentQuestionPO::getQuestionId, Collectors.mapping(AssessmentQuestionPO::getPoId, Collectors.toList())));

                for (AssessmentQuestion q : questions) {
                    List<Integer> poIds = poIdsByQuestion.getOrDefault(q.getId(), List.of());
                    if (poIds.isEmpty()) continue;
                    List<String> codes = new ArrayList<>();
                    for (Integer poId : poIds) {
                        String po = poNumberById.get(poId);
                        if (po == null) continue;
                        codes.add(po);
                        codeSet.add(po);
                        maxByTypeCode.merge(type + "::" + po, q.getMarks(), Double::sum);
                    }
                    if (!codes.isEmpty()) {
                        questionIdToCodes.put(q.getId(), codes);
                        questionIdToType.put(q.getId(), type);
                    }
                }
            }
        }

        List<ConsolidatedRow> rows = new ArrayList<>();
        for (String studentId : studentIds) {
            Map<String, Double> obtainedByTypeCode = new HashMap<>();
            for (Map.Entry<Integer, List<String>> entry : questionIdToCodes.entrySet()) {
                Double obtained = obtainedByKey.get(studentId + "::" + entry.getKey());
                if (obtained == null) continue;
                String type = questionIdToType.get(entry.getKey());
                for (String code : entry.getValue()) {
                    obtainedByTypeCode.merge(type + "::" + code, obtained, Double::sum);
                }
            }
            rows.add(buildConsolidatedRow(studentId, studentNames, presentTypes, codeSet, maxByTypeCode, obtainedByTypeCode));
        }
        rows.sort(Comparator.comparing(ConsolidatedRow::studentName));

        return new TypeAggregate(presentTypes, List.copyOf(codeSet), maxByTypeCode, rows);
    }

    private List<ConsolidatedRow> buildConsolidatedRows(
        List<String> studentIds, Map<String, String> studentNames, Map<String, Double> obtainedByKey,
        List<String> presentTypes, Set<String> codeSet, Map<String, Double> maxByTypeCode,
        Map<Integer, String> questionIdToCode, Map<Integer, String> questionIdToType
    ) {
        List<ConsolidatedRow> rows = new ArrayList<>();
        for (String studentId : studentIds) {
            Map<String, Double> obtainedByTypeCode = new HashMap<>();
            for (Map.Entry<Integer, String> entry : questionIdToCode.entrySet()) {
                Double obtained = obtainedByKey.get(studentId + "::" + entry.getKey());
                if (obtained == null) continue;
                String type = questionIdToType.get(entry.getKey());
                obtainedByTypeCode.merge(type + "::" + entry.getValue(), obtained, Double::sum);
            }
            rows.add(buildConsolidatedRow(studentId, studentNames, presentTypes, codeSet, maxByTypeCode, obtainedByTypeCode));
        }
        rows.sort(Comparator.comparing(ConsolidatedRow::studentName));
        return rows;
    }

    private ConsolidatedRow buildConsolidatedRow(
        String studentId, Map<String, String> studentNames, List<String> presentTypes, Set<String> codeSet,
        Map<String, Double> maxByTypeCode, Map<String, Double> obtainedByTypeCode
    ) {
        Map<String, Double> percentByTypeCode = new HashMap<>();
        for (String type : presentTypes) {
            for (String code : codeSet) {
                double max = maxByTypeCode.getOrDefault(type + "::" + code, 0.0);
                if (max <= 0) continue;
                double got = obtainedByTypeCode.getOrDefault(type + "::" + code, 0.0);
                percentByTypeCode.put(type + "::" + code, got / max * 100.0);
            }
        }

        Map<String, Double> totalPercentByCode = new HashMap<>();
        double grandMax = 0, grandGot = 0;
        for (String code : codeSet) {
            double maxSum = 0, gotSum = 0;
            for (String type : presentTypes) {
                maxSum += maxByTypeCode.getOrDefault(type + "::" + code, 0.0);
                gotSum += obtainedByTypeCode.getOrDefault(type + "::" + code, 0.0);
            }
            if (maxSum > 0) totalPercentByCode.put(code, gotSum / maxSum * 100.0);
            grandMax += maxSum;
            grandGot += gotSum;
        }
        double grandTotalPercent = grandMax > 0 ? grandGot / grandMax * 100.0 : 0.0;

        return new ConsolidatedRow(studentId, studentNames.getOrDefault(studentId, studentId), percentByTypeCode, totalPercentByCode, grandTotalPercent);
    }

    private Map<String, List<CourseAssessmentSection>> groupSectionsByType(List<CourseAssessmentSection> sections) {
        Map<String, List<CourseAssessmentSection>> byType = new LinkedHashMap<>();
        for (String type : TYPE_ORDER) byType.put(type, new ArrayList<>());
        for (CourseAssessmentSection section : sections) byType.get(classifyType(section.getDisplayName())).add(section);
        return byType;
    }

    private String classifyType(String displayName) {
        String lower = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
        if (lower.contains("quiz")) return "Quiz";
        if (lower.contains("assignment")) return "Assignment";
        if (lower.contains("lab")) return "Lab";
        if (lower.contains("project")) return "Project";
        if (lower.contains("mid")) return "Mid";
        if (lower.contains("final")) return "Final";
        return "Other";
    }

    // 13 fixed bands (Below 40, then 5-point steps to 95-100), counting each student's
    // overall (all-selected-sections) percentage for that CO - reuses the same
    // totalPercentByCode numbers already computed for the Consolidated Summary's Total
    // column rather than a fourth pass over the marks.
    private List<CoDistributionRow> buildCoDistribution(List<String> codes, List<ConsolidatedRow> rows) {
        List<CoDistributionRow> result = new ArrayList<>();
        for (String code : codes) {
            int[] counts = new int[DISTRIBUTION_BANDS.size()];
            for (ConsolidatedRow row : rows) {
                Double pct = row.totalPercentByCode().get(code);
                if (pct == null) continue;
                counts[bandIndex(pct)]++;
            }
            List<Integer> boxed = new ArrayList<>();
            for (int count : counts) boxed.add(count);
            result.add(new CoDistributionRow(code, boxed));
        }
        return result;
    }

    private int bandIndex(double pct) {
        if (pct < 40) return 0;
        int band = (int) ((pct - 40) / 5) + 1;
        return Math.min(band, DISTRIBUTION_BANDS.size() - 1);
    }

    private static int extractNumber(String label) {
        try {
            return Integer.parseInt(label.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    // ============================== PDF rendering ==============================

    private String writeDetailedMarksPdf(
        String courseCode, String programme, String academicYear, String department, Course course,
        List<SectionReport> sectionReports, TypeAggregate consolidated, List<CoDistributionRow> distribution,
        boolean includeOverallCoAttainment, List<CoAttainmentTableRow> overallCoAttainment
    ) throws Exception {
        Path dir = resolveReportDir("detailed_marks_reports");
        Files.createDirectories(dir);

        String safeCourseCode = courseCode.replaceAll("[^A-Za-z0-9_-]", "");
        String safeProgramme = programme.replaceAll("[^A-Za-z0-9_-]", "");
        String fileName = "DetailedMarks_" + safeCourseCode + "_" + safeProgramme + "_" + academicYear + "_" + LocalDateTime.now().format(FILE_TS) + ".pdf";
        File outFile = dir.resolve(fileName).toFile();

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(outFile));
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf)) {

            doc.add(new Paragraph("DETAILED MARKS REPORT").setFontSize(16).setTextAlignment(TextAlignment.CENTER));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Course: " + courseCode + (course != null ? " - " + course.getCourseName() : "")));
            doc.add(new Paragraph("Programme: " + programme + "        Department: " + department));
            doc.add(new Paragraph("Academic Year: " + academicYear));
            doc.add(new Paragraph("Generated: " + LocalDateTime.now().format(TS)));

            for (SectionReport section : sectionReports) {
                doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                doc.add(new Paragraph(section.sectionName()).setFontSize(14).simulateBold());
                doc.add(new Paragraph(" "));

                SectionMarksTable table = section.marksTable();
                Table marksTable = new Table(table.questions().size() + 2);
                marksTable.setWidth(UnitValue.createPercentValue(100));
                marksTable.addHeaderCell(new Cell().add(new Paragraph("Student")));
                for (QuestionRow q : table.questions()) {
                    marksTable.addHeaderCell(new Cell().add(new Paragraph(q.title() + " (/" + fmt(q.maxMarks()) + ")")));
                }
                marksTable.addHeaderCell(new Cell().add(new Paragraph("Total")));
                for (StudentRowMarks row : table.students()) {
                    marksTable.addCell(new Cell().add(new Paragraph(row.studentName())));
                    for (QuestionRow q : table.questions()) {
                        Double obtained = row.marksByQuestionId().get(q.questionId());
                        marksTable.addCell(new Cell().add(new Paragraph(obtained != null ? fmt(obtained) : "-")));
                    }
                    marksTable.addCell(new Cell().add(new Paragraph(fmt(row.total()))));
                }
                doc.add(marksTable);
                doc.add(new Paragraph(" "));

                doc.add(new Paragraph("CO Attainment").simulateBold());
                if (section.coAttainment().isEmpty()) {
                    doc.add(new Paragraph("Not available - the questions in this section aren't mapped to any CO."));
                } else {
                    doc.add(coAttainmentTable(section.coAttainment()));
                }
            }

            doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
            doc.add(new Paragraph("Consolidated Marks Summary").setFontSize(14).simulateBold());
            doc.add(new Paragraph(" "));
            doc.add(consolidatedSummaryTable(consolidated));

            doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
            doc.add(new Paragraph("CO Distribution Analysis").setFontSize(14).simulateBold());
            doc.add(new Paragraph(" "));
            Table distTable = new Table(DISTRIBUTION_BANDS.size() + 1);
            distTable.setWidth(UnitValue.createPercentValue(100));
            distTable.addHeaderCell(new Cell().add(new Paragraph("CO")));
            for (String band : DISTRIBUTION_BANDS) distTable.addHeaderCell(new Cell().add(new Paragraph(band)));
            for (CoDistributionRow row : distribution) {
                distTable.addCell(new Cell().add(new Paragraph(row.code())));
                for (Integer count : row.bandCounts()) distTable.addCell(new Cell().add(new Paragraph(String.valueOf(count))));
            }
            doc.add(distTable);

            if (includeOverallCoAttainment) {
                doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                doc.add(new Paragraph("Overall CO Attainment Summary").setFontSize(14).simulateBold());
                doc.add(new Paragraph(" "));
                if (overallCoAttainment.isEmpty()) {
                    doc.add(new Paragraph("Not available - none of the selected sections' questions are mapped to a CO."));
                } else {
                    doc.add(coAttainmentTable(overallCoAttainment));
                }
            }
        }

        return fileName;
    }

    private Table coAttainmentTable(List<CoAttainmentTableRow> rows) {
        Table table = new Table(new float[]{1.5f, 2f, 2.5f, 2f, 2f});
        table.setWidth(UnitValue.createPercentValue(100));
        for (String header : new String[]{"CO", "Max Marks", "Students Above Threshold", "%", "Status"}) {
            table.addHeaderCell(new Cell().add(new Paragraph(header)));
        }
        for (CoAttainmentTableRow row : rows) {
            table.addCell(new Cell().add(new Paragraph(row.code())));
            table.addCell(new Cell().add(new Paragraph(fmt(row.maxMarks()))));
            table.addCell(new Cell().add(new Paragraph(row.studentsAboveThreshold() + " of " + row.totalStudents())));
            table.addCell(new Cell().add(new Paragraph(String.format(Locale.US, "%.2f", row.percent()))));
            table.addCell(new Cell().add(new Paragraph(row.attained() ? "Attained" : "Not Attained")));
        }
        return table;
    }

    private Table consolidatedSummaryTable(TypeAggregate aggregate) {
        int columns = 1 + (aggregate.types().size() * aggregate.codes().size()) + aggregate.codes().size() + 1;
        Table table = new Table(columns);
        table.setWidth(UnitValue.createPercentValue(100));
        table.addHeaderCell(new Cell().add(new Paragraph("Student")));
        for (String type : aggregate.types()) {
            for (String code : aggregate.codes()) {
                table.addHeaderCell(new Cell().add(new Paragraph(type + " " + code + " %")));
            }
        }
        for (String code : aggregate.codes()) {
            table.addHeaderCell(new Cell().add(new Paragraph("Total " + code + " %")));
        }
        table.addHeaderCell(new Cell().add(new Paragraph("Grand Total %")));

        for (ConsolidatedRow row : aggregate.rows()) {
            table.addCell(new Cell().add(new Paragraph(row.studentName())));
            for (String type : aggregate.types()) {
                for (String code : aggregate.codes()) {
                    Double pct = row.percentByTypeCode().get(type + "::" + code);
                    table.addCell(new Cell().add(new Paragraph(pct != null ? String.format(Locale.US, "%.2f", pct) : "-")));
                }
            }
            for (String code : aggregate.codes()) {
                Double pct = row.totalPercentByCode().get(code);
                table.addCell(new Cell().add(new Paragraph(pct != null ? String.format(Locale.US, "%.2f", pct) : "-")));
            }
            table.addCell(new Cell().add(new Paragraph(String.format(Locale.US, "%.2f", row.grandTotalPercent()))));
        }
        return table;
    }

    private String fmt(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format(Locale.US, "%.1f", value);
    }

    // ============================== Excel rendering ==============================

    private String writeConsolidatedMarksExcel(
        String courseCode, String programme, String academicYear, String department, Course course,
        TypeAggregate coAggregate, TypeAggregate poAggregate
    ) throws Exception {
        Path dir = resolveReportDir("consolidated_marks_reports");
        Files.createDirectories(dir);

        String safeCourseCode = courseCode.replaceAll("[^A-Za-z0-9_-]", "");
        String safeProgramme = programme.replaceAll("[^A-Za-z0-9_-]", "");
        String fileName = "ConsolidatedMarks_" + safeCourseCode + "_" + safeProgramme + "_" + academicYear + "_" + LocalDateTime.now().format(FILE_TS) + ".xlsx";
        File outFile = dir.resolve(fileName).toFile();

        try (Workbook workbook = new XSSFWorkbook(); FileOutputStream fos = new FileOutputStream(outFile)) {
            Sheet infoSheet = workbook.createSheet("Course Info");
            writeInfoSheet(infoSheet, courseCode, programme, academicYear, department, course);

            Sheet coSheet = workbook.createSheet("CO Consolidated Marks");
            writeAggregateSheet(coSheet, coAggregate, "CO");

            Sheet poSheet = workbook.createSheet("PO Consolidated Marks");
            writeAggregateSheet(poSheet, poAggregate, "PO");

            workbook.write(fos);
        }

        return fileName;
    }

    private void writeInfoSheet(Sheet sheet, String courseCode, String programme, String academicYear, String department, Course course) {
        int r = 0;
        writeRow(sheet, r++, "Course Code", courseCode);
        writeRow(sheet, r++, "Course Name", course != null ? course.getCourseName() : "");
        writeRow(sheet, r++, "Programme", programme);
        writeRow(sheet, r++, "Department", department);
        writeRow(sheet, r++, "Academic Year", academicYear);
        writeRow(sheet, r++, "Credits", course != null && course.getCredits() != null ? String.valueOf(course.getCredits()) : "");
        writeRow(sheet, r, "Generated", LocalDateTime.now().format(TS));
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void writeRow(Sheet sheet, int rowIndex, String label, String value) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
    }

    private void writeAggregateSheet(Sheet sheet, TypeAggregate aggregate, String codeLabel) {
        Row header = sheet.createRow(0);
        int col = 0;
        header.createCell(col++).setCellValue("Student");
        List<String> columnKeys = new ArrayList<>();
        for (String type : aggregate.types()) {
            for (String code : aggregate.codes()) {
                header.createCell(col++).setCellValue(type + " " + code + " %");
                columnKeys.add(type + "::" + code);
            }
        }
        for (String code : aggregate.codes()) {
            header.createCell(col++).setCellValue("Total " + code + " %");
        }
        header.createCell(col).setCellValue("Grand Total %");

        int rowIndex = 1;
        double[] columnSums = new double[columnKeys.size() + aggregate.codes().size() + 1];
        int[] columnCounts = new int[columnSums.length];

        for (ConsolidatedRow dataRow : aggregate.rows()) {
            Row row = sheet.createRow(rowIndex++);
            int c = 0;
            row.createCell(c++).setCellValue(dataRow.studentName());

            for (String key : columnKeys) {
                Double pct = dataRow.percentByTypeCode().get(key);
                if (pct != null) {
                    row.createCell(c).setCellValue(pct);
                    columnSums[c - 1] += pct;
                    columnCounts[c - 1]++;
                } else {
                    row.createCell(c).setCellValue("-");
                }
                c++;
            }
            for (String code : aggregate.codes()) {
                Double pct = dataRow.totalPercentByCode().get(code);
                if (pct != null) {
                    row.createCell(c).setCellValue(pct);
                    columnSums[c - 1] += pct;
                    columnCounts[c - 1]++;
                } else {
                    row.createCell(c).setCellValue("-");
                }
                c++;
            }
            row.createCell(c).setCellValue(dataRow.grandTotalPercent());
            columnSums[c] += dataRow.grandTotalPercent();
            columnCounts[c]++;
        }

        Row averageRow = sheet.createRow(rowIndex);
        averageRow.createCell(0).setCellValue("Class Average (%)");
        for (int i = 0; i < columnSums.length; i++) {
            double avg = columnCounts[i] > 0 ? columnSums[i] / columnCounts[i] : 0.0;
            averageRow.createCell(i + 1).setCellValue(avg);
        }

        for (int i = 0; i <= columnSums.length; i++) sheet.autoSizeColumn(i);
    }

    // Mirrors CourseReportService/FacultyReportService/SummaryReportService's
    // resolveReportDir - prefer the legacy desktop app's own output folder if present.
    private Path resolveReportDir(String dirName) {
        Path preferred = Paths.get("..", "CO_PO_Assessment", dirName).normalize();
        if (Files.exists(preferred.getParent())) {
            return preferred;
        }
        return Paths.get(dirName).normalize();
    }
}
