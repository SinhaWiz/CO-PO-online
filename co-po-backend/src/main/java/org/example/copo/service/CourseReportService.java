package org.example.copo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import org.example.copo.entity.Course;
import org.example.copo.entity.CourseReportDraft;
import org.example.copo.entity.Enrollment;
import org.example.copo.entity.Faculty;
import org.example.copo.repository.CORepository;
import org.example.copo.repository.CourseRepository;
import org.example.copo.repository.CourseReportDraftRepository;
import org.example.copo.repository.EnrollmentRepository;
import org.example.copo.repository.FacultyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ports the desktop app's Course Report screen (CourseReportDialogController +
 * CourseReportDraftManager). It reads as an intentionally qualitative self-assessment
 * form, not a computed one: aside from a handful of auto-filled seed values (enrolled
 * count, configured CO codes, faculty name, threshold default), every number and every
 * remark is typed in by the faculty member - there's no hidden marks-based CO
 * calculator to reimplement here, that was already removed on the desktop side.
 *
 * The one real behavior change from desktop: drafts now live in CourseReportDraft
 * (DB-backed, keyed by assignment + faculty id) instead of a static in-memory map, so
 * a draft survives a restart and can't leak to a different faculty member if the
 * course assignment is ever reassigned.
 */
@Service
@RequiredArgsConstructor
public class CourseReportService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CourseReportDraftRepository draftRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final FacultyRepository facultyRepository;
    private final CORepository coRepository;
    private final CourseOutcomeService courseOutcomeService;
    private final CourseAssignmentThresholdService thresholdService;
    private final AssignmentAuthorizationService authorizationService;
    private final ObjectMapper objectMapper;
    private final ReportStorageService reportStorage;

    public record GradeRow(String letter, Integer count) {}
    public record CoAttainmentRow(String coCode, Double maxMarks, Integer studentsAttained, Double attainmentPercent, String remarks) {}
    public record TopicRow(String topic, Double hours, String instructor) {}
    public record AssessmentMethodRow(String method, Double percent, boolean readOnly) {}
    public record ActionPlanRow(String action, String completionDate, String responsible) {}

    public record CourseReportForm(
        String lectureHours, String tutorialHours, String practicalHours,
        String instructors, String hodName,
        String studentsCompleting, String percentPassed, String percentFailed,
        List<GradeRow> gradeDistribution,
        List<CoAttainmentRow> coAttainment,
        List<TopicRow> topics,
        String coverageLevel, String topicDeviation,
        boolean methodLectures, boolean methodLab, boolean methodSeminar,
        boolean methodActivity, boolean methodCaseStudy, boolean methodAssignment,
        String otherMethods,
        List<AssessmentMethodRow> assessmentMethods,
        String facilitiesLevel, String inadequacies,
        String adminConstraints, String studentCriticism, String moderatorComments,
        String externalComments, String enhancementProgress,
        List<ActionPlanRow> actionPlan,
        String thresholdOverride
    ) {}

    public record CourseReportSeed(
        String courseName, Double credits, String semester, String department,
        int totalStudents, String instructorDefault, List<String> coCodes,
        List<String> letterGrades, Double coCohortThresholdDefault,
        String nextAcademicYear, List<AssessmentMethodRow> assessmentMethodDefaults
    ) {}

    public record CourseReportContext(CourseReportSeed seed, CourseReportForm draft, String updatedAt) {}

    public record GenerateResult(String pdfFileName, List<String> issues) {}

    private static final List<String> FIXED_ASSESSMENT_METHODS = List.of(
        "Written Exam", "Oral Exam", "Practical/Lab", "Other assignments", "Total", "External evaluator involvement"
    );

    @Transactional(readOnly = true)
    public CourseReportContext getContext(String facultyEmail, String courseCode, String programme, String academicYear, String department) {
        authorizationService.requireAssignedToCourse(facultyEmail, courseCode, programme, academicYear);
        CourseReportSeed seed = buildSeed(facultyEmail, courseCode, programme, academicYear, department);

        String facultyId = authorizationService.resolveFacultyId(facultyEmail);
        return draftRepository
            .findByCourseCodeAndProgrammeAndAcademicYearAndDepartmentAndFacultyId(courseCode, programme, academicYear, department, facultyId)
            .map(saved -> new CourseReportContext(seed, parseForm(saved.getFormJson()), saved.getUpdatedAt().format(TS)))
            .orElseGet(() -> new CourseReportContext(seed, null, null));
    }

    @Transactional
    public String saveDraft(String facultyEmail, String courseCode, String programme, String academicYear, String department, CourseReportForm form) {
        authorizationService.requireAssignedToCourse(facultyEmail, courseCode, programme, academicYear);
        String facultyId = authorizationService.resolveFacultyId(facultyEmail);

        CourseReportDraft draft = draftRepository
            .findByCourseCodeAndProgrammeAndAcademicYearAndDepartmentAndFacultyId(courseCode, programme, academicYear, department, facultyId)
            .orElseGet(() -> {
                CourseReportDraft fresh = new CourseReportDraft();
                fresh.setCourseCode(courseCode);
                fresh.setProgramme(programme);
                fresh.setAcademicYear(academicYear);
                fresh.setDepartment(department);
                fresh.setFacultyId(facultyId);
                return fresh;
            });

        draft.setFormJson(writeForm(form));
        LocalDateTime now = LocalDateTime.now();
        draft.setUpdatedAt(now);
        draftRepository.save(draft);
        return now.format(TS);
    }

    @Transactional
    public void clearDraft(String facultyEmail, String courseCode, String programme, String academicYear, String department) {
        authorizationService.requireAssignedToCourse(facultyEmail, courseCode, programme, academicYear);
        String facultyId = authorizationService.resolveFacultyId(facultyEmail);
        draftRepository.deleteByCourseCodeAndProgrammeAndAcademicYearAndDepartmentAndFacultyId(
            courseCode, programme, academicYear, department, facultyId
        );
    }

    @Transactional
    public GenerateResult generateReport(
        String facultyEmail, String courseCode, String programme, String academicYear, String department, CourseReportForm form
    ) {
        authorizationService.requireAssignedToCourse(facultyEmail, courseCode, programme, academicYear);
        CourseReportSeed seed = buildSeed(facultyEmail, courseCode, programme, academicYear, department);

        List<String> issues = validate(form, seed);
        if (!issues.isEmpty()) {
            return new GenerateResult(null, issues);
        }

        Faculty faculty = facultyRepository.findByEmail(facultyEmail).orElse(null);
        String teacherName = faculty != null ? faculty.getFullName() : facultyEmail;
        double threshold = Double.parseDouble(form.thresholdOverride().trim());

        try {
            String fileName = writeCourseReportPdf(
                courseCode, programme, academicYear, department, seed, form, teacherName, threshold
            );
            clearDraft(facultyEmail, courseCode, programme, academicYear, department);
            return new GenerateResult(fileName, List.of());
        } catch (Exception ex) {
            return new GenerateResult(null, List.of("Failed to save PDF: " + ex.getMessage()));
        }
    }

    private CourseReportSeed buildSeed(String facultyEmail, String courseCode, String programme, String academicYear, String department) {
        Course course = courseRepository.findById(new Course.CourseId(courseCode, programme)).orElse(null);
        String courseName = course != null ? course.getCourseName() : courseCode;
        Double credits = course != null ? course.getCredits() : null;

        List<Enrollment> enrollments = enrollmentRepository.findByCourseIdAndProgrammeAndAcademicYear(courseCode, programme, academicYear);
        int totalStudents = (int) enrollments.stream().map(Enrollment::getStudentId).distinct().count();

        String instructorDefault = facultyRepository.findByEmail(facultyEmail).map(Faculty::getFullName).orElse("");

        CourseOutcomeService.CourseOutcomesDto allowList = courseOutcomeService.getCourseOutcomes(courseCode, programme);
        List<String> coCodes = coRepository.findAllById(allowList.coIds()).stream()
            .map(org.example.copo.entity.CO::getCoNumber)
            .sorted()
            .toList();

        CourseAssignmentThresholdService.ThresholdsDto thresholds = thresholdService.getThresholds(courseCode, programme, academicYear, department);

        List<AssessmentMethodRow> assessmentMethodDefaults = FIXED_ASSESSMENT_METHODS.stream()
            .map(method -> new AssessmentMethodRow(method, "Total".equals(method) ? 100.0 : 0.0, true))
            .toList();

        return new CourseReportSeed(
            courseName, credits, determineSemester(courseCode), department,
            totalStudents, instructorDefault, coCodes,
            org.example.copo.service.GradeProcessor.getAllLetterGrades(), thresholds.coCohort(),
            calculateNextAcademicYear(academicYear), assessmentMethodDefaults
        );
    }

    // Odd digit at length-3 -> Winter offering, even -> Summer. Matches desktop's
    // determineSemester() exactly - it's a naming convention baked into course codes
    // in this institution, not something configurable.
    private String determineSemester(String courseCode) {
        if (courseCode == null || courseCode.length() < 3) return "";
        char digit = courseCode.charAt(courseCode.length() - 3);
        if (!Character.isDigit(digit)) return "";
        return (Character.getNumericValue(digit) % 2 == 1) ? "Winter" : "Summer";
    }

    // "2023-2024" -> "2024-2025", "23-24" -> "24-25" (2-digit wraparound via %02d).
    // Matches desktop's calculateNextAcademicYear() exactly.
    private String calculateNextAcademicYear(String academicYear) {
        if (academicYear == null || !academicYear.contains("-")) return academicYear;
        String[] parts = academicYear.split("-", 2);
        try {
            int start = Integer.parseInt(parts[0].trim());
            int end = Integer.parseInt(parts[1].trim());
            if (parts[0].trim().length() <= 2) {
                return String.format("%02d-%02d", (start + 1) % 100, (end + 1) % 100);
            }
            return (start + 1) + "-" + (end + 1);
        } catch (NumberFormatException ex) {
            return academicYear;
        }
    }

    private List<String> validate(CourseReportForm form, CourseReportSeed seed) {
        List<String> issues = new ArrayList<>();

        if (form.instructors() == null || form.instructors().isBlank()) {
            issues.add("Instructor name(s) are required.");
        }

        Double lecture = parseOrNull(form.lectureHours());
        Double tutorial = parseOrNull(form.tutorialHours());
        Double practical = parseOrNull(form.practicalHours());
        if (lecture == null || tutorial == null || practical == null) {
            issues.add("Lecture/Tutorial/Practical hours must be numbers.");
        } else if (seed.credits() != null && (lecture + tutorial + practical) > seed.credits() + 0.01) {
            issues.add("Lecture + Tutorial + Practical hours can't exceed the course's " + seed.credits() + " credits.");
        }

        Integer completing = parseIntOrNull(form.studentsCompleting());
        if (completing == null || completing < 0 || completing > seed.totalStudents()) {
            issues.add("Students completing must be a number between 0 and " + seed.totalStudents() + ".");
        }

        Double passed = parseOrNull(form.percentPassed());
        Double failed = parseOrNull(form.percentFailed());
        if (passed == null || failed == null || passed < 0 || passed > 100 || failed < 0 || failed > 100) {
            issues.add("Pass % and Fail % are required and must each be between 0 and 100.");
        } else if (Math.abs((passed + failed) - 100.0) > 0.1) {
            issues.add("Pass % and Fail % must add up to 100.");
        }

        Double threshold = parseOrNull(form.thresholdOverride());
        if (threshold == null) {
            issues.add("CO cohort attainment threshold is required.");
        }

        return issues;
    }

    private Double parseOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer parseIntOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String writeForm(CourseReportForm form) {
        try {
            return objectMapper.writeValueAsString(form);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize course report draft: " + ex.getMessage(), ex);
        }
    }

    private CourseReportForm parseForm(String json) {
        try {
            return objectMapper.readValue(json, CourseReportForm.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read saved course report draft: " + ex.getMessage(), ex);
        }
    }

    private String writeCourseReportPdf(
        String courseCode, String programme, String academicYear, String department,
        CourseReportSeed seed, CourseReportForm form, String teacherName, double threshold
    ) throws Exception {
        Path dir = reportStorage.resolveReportDir("course_reports");
        Files.createDirectories(dir);

        String safeCourseCode = courseCode.replaceAll("[^A-Za-z0-9_-]", "");
        String safeProgramme = programme.replaceAll("[^A-Za-z0-9_-]", "");
        String fileName = "Course_Report_" + safeCourseCode + "_" + safeProgramme + "_" + academicYear + ".pdf";
        File outFile = dir.resolve(fileName).toFile();

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(outFile));
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf)) {

            doc.add(new Paragraph("COURSE REPORT OF FACULTY MEMBER").setFontSize(16).setTextAlignment(TextAlignment.CENTER));
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("1. Basic Information").setFontSize(13).simulateBold());
            doc.add(new Paragraph("Programme: " + programme));
            doc.add(new Paragraph("Course: " + courseCode + " - " + seed.courseName()));
            doc.add(new Paragraph("Academic Year: " + academicYear));
            doc.add(new Paragraph("Semester: " + seed.semester()));
            doc.add(new Paragraph("Credit Hours: L=" + form.lectureHours() + " T=" + form.tutorialHours() + " P=" + form.practicalHours()));
            doc.add(new Paragraph("Instructor(s): " + form.instructors()));
            doc.add(new Paragraph("Head of Department: " + nullToNA(form.hodName())));
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("2. Statistical Information").setFontSize(13).simulateBold());
            doc.add(new Paragraph("Students Attending: " + seed.totalStudents()));
            doc.add(new Paragraph("Students Completing: " + form.studentsCompleting()));
            doc.add(new Paragraph("Passed: " + form.percentPassed() + "%   Failed: " + form.percentFailed() + "%"));
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("Distribution of Grades").simulateBold());
            Table gradeTable = new Table(new float[]{3f, 2f, 2f});
            gradeTable.setWidth(UnitValue.createPercentValue(60));
            gradeTable.addHeaderCell(new Cell().add(new Paragraph("Grade")));
            gradeTable.addHeaderCell(new Cell().add(new Paragraph("Count")));
            gradeTable.addHeaderCell(new Cell().add(new Paragraph("%")));
            for (GradeRow row : form.gradeDistribution()) {
                gradeTable.addCell(new Cell().add(new Paragraph(row.letter())));
                gradeTable.addCell(new Cell().add(new Paragraph(String.valueOf(row.count()))));
                double pct = seed.totalStudents() > 0 ? (row.count() * 100.0 / seed.totalStudents()) : 0.0;
                gradeTable.addCell(new Cell().add(new Paragraph(String.format(Locale.US, "%.2f", pct))));
            }
            doc.add(gradeTable);
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("CO Attainment (cohort threshold: " + threshold + "%)").simulateBold());
            Table coTable = new Table(new float[]{1.5f, 2f, 2.5f, 2f, 3f, 2f});
            coTable.setWidth(UnitValue.createPercentValue(100));
            for (String header : new String[]{"CO", "Max Marks", "Students Attained", "Attainment %", "Remarks", "Status"}) {
                coTable.addHeaderCell(new Cell().add(new Paragraph(header)));
            }
            for (CoAttainmentRow row : form.coAttainment()) {
                coTable.addCell(new Cell().add(new Paragraph(row.coCode())));
                coTable.addCell(new Cell().add(new Paragraph(String.valueOf(row.maxMarks()))));
                coTable.addCell(new Cell().add(new Paragraph(String.valueOf(row.studentsAttained()))));
                coTable.addCell(new Cell().add(new Paragraph(String.format(Locale.US, "%.2f", row.attainmentPercent()))));
                coTable.addCell(new Cell().add(new Paragraph(row.remarks() == null ? "" : row.remarks())));
                coTable.addCell(new Cell().add(new Paragraph(row.attainmentPercent() >= threshold ? "Attained" : "Not Attained")));
            }
            doc.add(coTable);
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("3. Course Related Information").setFontSize(13).simulateBold());

            doc.add(new Paragraph("Details of Course Taught").simulateBold());
            Table topicsTable = new Table(new float[]{5f, 2f, 3f});
            topicsTable.setWidth(UnitValue.createPercentValue(100));
            topicsTable.addHeaderCell(new Cell().add(new Paragraph("Topic")));
            topicsTable.addHeaderCell(new Cell().add(new Paragraph("Hours")));
            topicsTable.addHeaderCell(new Cell().add(new Paragraph("Instructor")));
            for (TopicRow row : form.topics()) {
                topicsTable.addCell(new Cell().add(new Paragraph(nullToEmpty(row.topic()))));
                topicsTable.addCell(new Cell().add(new Paragraph(String.valueOf(row.hours()))));
                topicsTable.addCell(new Cell().add(new Paragraph(nullToEmpty(row.instructor()))));
            }
            doc.add(topicsTable);
            doc.add(new Paragraph("Coverage: " + nullToNA(form.coverageLevel())));
            doc.add(new Paragraph("Deviation from plan: " + nullToNA(form.topicDeviation())));
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("Teaching and Learning Methods").simulateBold());
            doc.add(new Paragraph(teachingMethodsSummary(form)));
            if (form.otherMethods() != null && !form.otherMethods().isBlank()) {
                doc.add(new Paragraph("Other: " + form.otherMethods()));
            }
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("Student Assessment").simulateBold());
            Table assessmentTable = new Table(new float[]{5f, 2f});
            assessmentTable.setWidth(UnitValue.createPercentValue(60));
            assessmentTable.addHeaderCell(new Cell().add(new Paragraph("Method")));
            assessmentTable.addHeaderCell(new Cell().add(new Paragraph("%")));
            for (AssessmentMethodRow row : form.assessmentMethods()) {
                assessmentTable.addCell(new Cell().add(new Paragraph(row.method())));
                assessmentTable.addCell(new Cell().add(new Paragraph(String.valueOf(row.percent()))));
            }
            doc.add(assessmentTable);
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("Facilities and Teaching Materials: " + nullToNA(form.facilitiesLevel())));
            doc.add(new Paragraph("Inadequacies: " + nullToNA(form.inadequacies())));
            doc.add(new Paragraph(" "));

            addTextSection(doc, "Administrative/Organizational Constraints", form.adminConstraints());
            addTextSection(doc, "Student Evaluation / Criticism", form.studentCriticism());
            addTextSection(doc, "Moderator Comments", form.moderatorComments());
            addTextSection(doc, "External Evaluator Comments", form.externalComments());
            addTextSection(doc, "Course Enhancement Progress", form.enhancementProgress());

            doc.add(new Paragraph("Action Plan for " + seed.nextAcademicYear()).simulateBold());
            Table actionTable = new Table(new float[]{5f, 2f, 3f});
            actionTable.setWidth(UnitValue.createPercentValue(100));
            actionTable.addHeaderCell(new Cell().add(new Paragraph("Action")));
            actionTable.addHeaderCell(new Cell().add(new Paragraph("Completion Date")));
            actionTable.addHeaderCell(new Cell().add(new Paragraph("Responsible")));
            for (ActionPlanRow row : form.actionPlan()) {
                boolean blank = isBlank(row.action()) && isBlank(row.completionDate()) && isBlank(row.responsible());
                if (blank) continue;
                actionTable.addCell(new Cell().add(new Paragraph(nullToEmpty(row.action()))));
                actionTable.addCell(new Cell().add(new Paragraph(nullToEmpty(row.completionDate()))));
                actionTable.addCell(new Cell().add(new Paragraph(nullToEmpty(row.responsible()))));
            }
            doc.add(actionTable);
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("Course Teacher: " + teacherName + "        Signature: ________________        Date: ________________"));
            doc.add(new Paragraph("Head of the Department: " + nullToNA(form.hodName()) + "        Signature: ________________        Date: ________________"));
        }

        return fileName;
    }

    private void addTextSection(Document doc, String title, String value) {
        doc.add(new Paragraph(title).simulateBold());
        doc.add(new Paragraph(nullToNA(value)));
        doc.add(new Paragraph(" "));
    }

    private String teachingMethodsSummary(CourseReportForm form) {
        List<String> methods = new ArrayList<>();
        if (form.methodLectures()) methods.add("Lectures");
        if (form.methodLab()) methods.add("Lab");
        if (form.methodSeminar()) methods.add("Seminar");
        if (form.methodActivity()) methods.add("Activity");
        if (form.methodCaseStudy()) methods.add("Case Study");
        if (form.methodAssignment()) methods.add("Assignment");
        return methods.isEmpty() ? "None selected" : String.join(", ", methods);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String nullToNA(String value) {
        return (value == null || value.isBlank()) ? "N/A" : value;
    }
}
