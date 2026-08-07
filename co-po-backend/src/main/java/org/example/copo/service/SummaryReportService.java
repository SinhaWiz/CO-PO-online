package org.example.copo.service;

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
import org.example.copo.entity.Student;
import org.example.copo.repository.CourseRepository;
import org.example.copo.repository.StudentRepository;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ports the desktop app's Summary Report screen (SummaryReportDialogController). No
 * draft persistence here, unlike Course Report - desktop never built one for this
 * screen either, and with only 3 short feedback fields (vs. Course Report's ~20+
 * fields across 5 sections) there's much less to lose by not having one.
 *
 * The evaluation sheet and attainment histogram both build on
 * AttainmentService.buildCoMatrix() - the same completeness gating and per-student,
 * per-CO marks matrix the CO Report already computes, just consumed at a finer grain
 * here instead of collapsed to one aggregate percentage per CO.
 *
 * One deliberate deviation from desktop: it rendered "Assessment Requirements" (which
 * of 7 fixed brackets the configured CO-individual threshold falls into) as its own
 * section, separate from the "Attainment Status" sentences later in the report - and
 * the two sections failed inconsistently when the threshold didn't land on a standard
 * bracket (one silently rendered an empty table, the other printed an honest "could
 * not determine" message). Since both are really the same computation, this port
 * folds them into one Attainment Status section that always states the rule and, when
 * the threshold matches a standard bracket, the per-CO verdict alongside it.
 */
@Service
@RequiredArgsConstructor
public class SummaryReportService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final List<String> BUCKET_LABELS = List.of(
        "Below 40%", "40% and above", "50% and above", "60% and above", "70% and above", "80% and above", "90% and above"
    );
    private static final List<Double> BUCKET_THRESHOLDS = List.of(0.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0);

    private final AttainmentService attainmentService;
    private final CourseAssignmentThresholdService thresholdService;
    private final AssignmentAuthorizationService authorizationService;
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final ReportStorageService reportStorage;

    public record EvaluationRow(String studentId, String studentName, Map<String, Double> obtainedByCo) {}
    public record CoHistogramRow(String coCode, List<Integer> bucketCounts) {}
    public record AttainmentStatusRow(String coCode, double achievedPercent, boolean satisfied) {}

    public record SummaryReportPreview(
        String courseName, Double credits, String semester, int majorityBatch, int totalStudents,
        double coIndividualThreshold, double coCohortThreshold,
        List<String> coCodes, Map<String, Double> coMaxMarks,
        List<EvaluationRow> evaluationSheet, List<CoHistogramRow> histogram,
        boolean thresholdInStandardRange, List<AttainmentStatusRow> attainmentStatus,
        List<String> issues
    ) {}

    public record GenerateSummaryReportRequest(String feedback1, String feedback2, String improvementPlan) {}
    public record GenerateResult(String pdfFileName, List<String> issues) {}

    public SummaryReportPreview getPreview(String facultyEmail, String courseCode, String programme, String academicYear, String department) {
        authorizationService.requireAssignedToCourse(facultyEmail, courseCode, programme, academicYear);
        return buildPreview(courseCode, programme, academicYear, department);
    }

    public GenerateResult generateReport(
        String facultyEmail, String courseCode, String programme, String academicYear, String department,
        GenerateSummaryReportRequest request
    ) {
        authorizationService.requireAssignedToCourse(facultyEmail, courseCode, programme, academicYear);
        SummaryReportPreview preview = buildPreview(courseCode, programme, academicYear, department);
        if (!preview.issues().isEmpty()) {
            return new GenerateResult(null, preview.issues());
        }

        GenerateSummaryReportRequest truncated = new GenerateSummaryReportRequest(
            truncate(request != null ? request.feedback1() : null, 500),
            truncate(request != null ? request.feedback2() : null, 700),
            truncate(request != null ? request.improvementPlan() : null, 1000)
        );

        try {
            String fileName = writeSummaryReportPdf(courseCode, programme, academicYear, department, preview, truncated);
            return new GenerateResult(fileName, List.of());
        } catch (Exception ex) {
            return new GenerateResult(null, List.of("Failed to save PDF: " + ex.getMessage()));
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private SummaryReportPreview buildPreview(String courseCode, String programme, String academicYear, String department) {
        AttainmentService.CoMatrix matrix = attainmentService.buildCoMatrix(courseCode, programme, academicYear);
        if (!matrix.issues().isEmpty()) {
            return new SummaryReportPreview(
                courseCode, null, "", 0, 0, 0, 0, List.of(), Map.of(), List.of(), List.of(), false, List.of(), matrix.issues()
            );
        }

        Course course = courseRepository.findById(new Course.CourseId(courseCode, programme)).orElse(null);
        List<Student> students = studentRepository.findAllById(matrix.studentIds());
        Map<String, String> studentNames = new HashMap<>();
        for (Student s : students) studentNames.put(s.getId(), s.getName());

        CourseAssignmentThresholdService.ThresholdsDto thresholds = thresholdService.getThresholds(courseCode, programme, academicYear, department);

        List<String> coCodes = matrix.coTotal().keySet().stream()
            .sorted(Comparator.comparingInt(SummaryReportService::extractNumber))
            .toList();

        List<EvaluationRow> evaluationSheet = matrix.studentIds().stream()
            .map(id -> new EvaluationRow(id, studentNames.getOrDefault(id, id), matrix.studentCoObtained().get(id)))
            .sorted(Comparator.comparing(EvaluationRow::studentName))
            .toList();

        List<CoHistogramRow> histogram = new ArrayList<>();
        for (String co : coCodes) {
            double max = matrix.coTotal().get(co);
            List<Integer> counts = new ArrayList<>();
            for (int bucket = 0; bucket < BUCKET_THRESHOLDS.size(); bucket++) {
                double boundary = BUCKET_THRESHOLDS.get(bucket);
                int count = 0;
                for (String studentId : matrix.studentIds()) {
                    double got = matrix.studentCoObtained().get(studentId).getOrDefault(co, 0.0);
                    double pct = max > 0 ? (got / max) * 100.0 : 0.0;
                    boolean inBucket = bucket == 0 ? pct < 40.0 : pct >= boundary;
                    if (inBucket) count++;
                }
                counts.add(count);
            }
            histogram.add(new CoHistogramRow(co, counts));
        }

        int targetBucket = -1;
        for (int i = 0; i < BUCKET_THRESHOLDS.size(); i++) {
            if (Math.abs(BUCKET_THRESHOLDS.get(i) - thresholds.coIndividual()) < 0.1) {
                targetBucket = i;
                break;
            }
        }

        List<AttainmentStatusRow> attainmentStatus = List.of();
        if (targetBucket != -1) {
            List<AttainmentStatusRow> rows = new ArrayList<>();
            for (CoHistogramRow row : histogram) {
                double achievedPct = matrix.studentIds().isEmpty() ? 0.0 : (row.bucketCounts().get(targetBucket) * 100.0) / matrix.studentIds().size();
                rows.add(new AttainmentStatusRow(row.coCode(), achievedPct, achievedPct >= thresholds.coCohort()));
            }
            attainmentStatus = rows;
        }

        return new SummaryReportPreview(
            course != null ? course.getCourseName() : courseCode,
            course != null ? course.getCredits() : null,
            determineSemester(courseCode),
            computeMajorityBatch(students),
            matrix.studentIds().size(),
            thresholds.coIndividual(), thresholds.coCohort(),
            coCodes, matrix.coTotal(),
            evaluationSheet, histogram,
            targetBucket != -1, attainmentStatus,
            List.of()
        );
    }

    // Odd digit at length-3 -> Winter, even -> Summer - the exact same institutional
    // convention already ported in CourseReportService.determineSemester().
    private String determineSemester(String courseCode) {
        if (courseCode == null || courseCode.length() < 3) return "";
        char digit = courseCode.charAt(courseCode.length() - 3);
        if (!Character.isDigit(digit)) return "";
        return (Character.getNumericValue(digit) % 2 == 1) ? "Winter" : "Summer";
    }

    // Mode of enrolled students' batch numbers, ties broken toward the lower batch -
    // the same rule AttendanceService/CourseResultService already use. Desktop's own
    // version of this had no tie-break at all (arbitrary on ties, since it relied on
    // HashMap iteration order) - that's not a behavior worth replicating faithfully,
    // it's just a bug, so this uses the deterministic rule already established
    // elsewhere in this codebase instead.
    private int computeMajorityBatch(List<Student> students) {
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

    private static int extractNumber(String label) {
        try {
            return Integer.parseInt(label.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    private String writeSummaryReportPdf(
        String courseCode, String programme, String academicYear, String department,
        SummaryReportPreview preview, GenerateSummaryReportRequest request
    ) throws Exception {
        Path dir = reportStorage.resolveReportDir("summary_reports");
        Files.createDirectories(dir);

        String safeCourseCode = courseCode.replaceAll("[^A-Za-z0-9_-]", "");
        String safeProgramme = programme.replaceAll("[^A-Za-z0-9_-]", "");
        String fileName = "Summary_Report_" + safeCourseCode + "_" + safeProgramme + "_" + academicYear + ".pdf";
        File outFile = dir.resolve(fileName).toFile();

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(outFile));
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf)) {

            doc.add(new Paragraph("SUMMARY REPORT").setFontSize(16).setTextAlignment(TextAlignment.CENTER));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Course: " + courseCode + " - " + preview.courseName()));
            doc.add(new Paragraph("Programme: " + programme + "        Department: " + department));
            doc.add(new Paragraph("Academic Year: " + academicYear + "        Semester: " + preview.semester()));
            doc.add(new Paragraph("Batch: " + (preview.majorityBatch() > 0 ? preview.majorityBatch() : "N/A")
                + "        Credits: " + (preview.credits() != null ? preview.credits() : "N/A")));
            doc.add(new Paragraph("Generated: " + LocalDateTime.now().format(TS)));
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("1. Evaluation Sheet for COs").setFontSize(13).simulateBold());
            Table evalTable = new Table(preview.coCodes().size() + 1);
            evalTable.setWidth(UnitValue.createPercentValue(100));
            evalTable.addHeaderCell(new Cell().add(new Paragraph("Student")));
            for (String co : preview.coCodes()) {
                evalTable.addHeaderCell(new Cell().add(new Paragraph(co + " (/" + fmt(preview.coMaxMarks().get(co)) + ")")));
            }
            for (EvaluationRow row : preview.evaluationSheet()) {
                evalTable.addCell(new Cell().add(new Paragraph(row.studentName())));
                for (String co : preview.coCodes()) {
                    double obtained = row.obtainedByCo().getOrDefault(co, 0.0);
                    evalTable.addCell(new Cell().add(new Paragraph(fmt(obtained))));
                }
            }
            doc.add(evalTable);
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("2. Statistical Analysis: CO Attainment Levels").setFontSize(13).simulateBold());
            Table histTable = new Table(BUCKET_LABELS.size() + 1);
            histTable.setWidth(UnitValue.createPercentValue(100));
            histTable.addHeaderCell(new Cell().add(new Paragraph("CO")));
            for (String label : BUCKET_LABELS) {
                histTable.addHeaderCell(new Cell().add(new Paragraph(label)));
            }
            for (CoHistogramRow row : preview.histogram()) {
                histTable.addCell(new Cell().add(new Paragraph(row.coCode())));
                for (Integer count : row.bucketCounts()) {
                    histTable.addCell(new Cell().add(new Paragraph(String.valueOf(count))));
                }
            }
            doc.add(histTable);
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("Attainment Status").simulateBold());
            doc.add(new Paragraph(String.format(
                Locale.US, "Rule: a CO is attained by a student at >= %.0f%% of its marks; the course meets its target when >= %.0f%% of students attain it.",
                preview.coIndividualThreshold(), preview.coCohortThreshold()
            )));
            if (preview.thresholdInStandardRange()) {
                for (AttainmentStatusRow row : preview.attainmentStatus()) {
                    String status = row.satisfied() ? "Attainment criteria satisfied" : "Attainment criteria not satisfied";
                    doc.add(new Paragraph(row.coCode() + " - " + status + String.format(Locale.US, " (%.1f%% of students)", row.achievedPercent())));
                }
            } else {
                doc.add(new Paragraph(String.format(
                    Locale.US, "Could not determine attainment status - the CO individual threshold (%.1f%%) isn't one of the standard 40/50/60/70/80/90 brackets.",
                    preview.coIndividualThreshold()
                )));
            }
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("3. Feedback & Comments").setFontSize(13).simulateBold());
            addTextBlock(doc, "Summary of feedback from student", request != null ? request.feedback1() : null);
            addTextBlock(doc, "Feedback and comments from course teacher", request != null ? request.feedback2() : null);
            addTextBlock(doc, "Improvement Plan", request != null ? request.improvementPlan() : null);
        }

        return fileName;
    }

    private void addTextBlock(Document doc, String title, String value) {
        doc.add(new Paragraph(title + ":").simulateBold());
        doc.add(new Paragraph(value == null || value.isBlank() ? "N/A" : value));
        doc.add(new Paragraph(" "));
    }

    private String fmt(Double value) {
        if (value == null) return "0";
        return value == Math.floor(value) ? String.valueOf(value.intValue()) : String.format(Locale.US, "%.1f", value);
    }
}
