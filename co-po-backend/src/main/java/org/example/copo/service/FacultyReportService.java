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
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Faculty-facing per-assignment reports (CO, PO, ... more added in later commits),
 * generated as PDFs matching the desktop app's report shape - a data table with
 * editable comment/suggestion columns, not the JFreeChart bar image the desktop
 * version embeds. AdminReportService's cohort PO report already established that
 * precedent (data table, no chart image) for this backend; charting stays a
 * frontend concern (recharts) rather than a server-side rendering dependency.
 */
@Service
@RequiredArgsConstructor
public class FacultyReportService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int COMMENT_MAX_LENGTH = 80;

    private final AttainmentService attainmentService;

    public record ReportCommentInput(String code, String comment, String suggestions) {}
    public record GenerateReportRequest(List<ReportCommentInput> comments) {}
    public record GeneratedReportResult(String pdfFileName, List<AttainmentService.OutcomeAttainmentRow> rows, List<String> issues) {}

    public GeneratedReportResult generateCoReport(
        String facultyEmail, String courseCode, String programme, String academicYear, String department,
        GenerateReportRequest request
    ) {
        AttainmentService.AttainmentResult attainment =
            attainmentService.getCoAttainment(facultyEmail, courseCode, programme, academicYear, department);
        if (!attainment.issues().isEmpty()) {
            return new GeneratedReportResult(null, List.of(), attainment.issues());
        }

        try {
            String fileName = writeOutcomeReportPdf(
                "CO", "co_reports", courseCode, programme, academicYear, department,
                attainment.rows(), commentsByCode(request)
            );
            return new GeneratedReportResult(fileName, attainment.rows(), List.of());
        } catch (Exception ex) {
            return new GeneratedReportResult(null, attainment.rows(), List.of("Failed to save PDF: " + ex.getMessage()));
        }
    }

    public GeneratedReportResult generatePoReport(
        String facultyEmail, String courseCode, String programme, String academicYear, String department,
        GenerateReportRequest request
    ) {
        AttainmentService.AttainmentResult attainment =
            attainmentService.getPoAttainment(facultyEmail, courseCode, programme, academicYear, department);
        if (!attainment.issues().isEmpty()) {
            return new GeneratedReportResult(null, List.of(), attainment.issues());
        }

        try {
            String fileName = writeOutcomeReportPdf(
                "PO", "po_reports", courseCode, programme, academicYear, department,
                attainment.rows(), commentsByCode(request)
            );
            return new GeneratedReportResult(fileName, attainment.rows(), List.of());
        } catch (Exception ex) {
            return new GeneratedReportResult(null, attainment.rows(), List.of("Failed to save PDF: " + ex.getMessage()));
        }
    }

    private Map<String, ReportCommentInput> commentsByCode(GenerateReportRequest request) {
        Map<String, ReportCommentInput> byCode = new HashMap<>();
        if (request == null || request.comments() == null) {
            return byCode;
        }
        for (ReportCommentInput comment : request.comments()) {
            if (comment == null || comment.code() == null) continue;
            byCode.put(
                comment.code(),
                new ReportCommentInput(comment.code(), truncate(comment.comment()), truncate(comment.suggestions()))
            );
        }
        return byCode;
    }

    private String truncate(String value) {
        if (value == null) return null;
        return value.length() > COMMENT_MAX_LENGTH ? value.substring(0, COMMENT_MAX_LENGTH) : value;
    }

    private String writeOutcomeReportPdf(
        String reportType, String outputDir, String courseCode, String programme, String academicYear, String department,
        List<AttainmentService.OutcomeAttainmentRow> rows, Map<String, ReportCommentInput> commentsByCode
    ) throws Exception {
        Path dir = resolveReportDir(outputDir);
        Files.createDirectories(dir);

        String safeCourseCode = courseCode.replaceAll("[^A-Za-z0-9_-]", "");
        String safeProgramme = programme.replaceAll("[^A-Za-z0-9_-]", "");
        String fileName = reportType + "_" + safeCourseCode + "_" + academicYear + "_" + safeProgramme + ".pdf";
        File outFile = dir.resolve(fileName).toFile();

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(outFile));
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf)) {

            Paragraph title = new Paragraph(courseCode + " - " + reportType + " Report")
                .setFontSize(16)
                .setTextAlignment(TextAlignment.CENTER);
            doc.add(title);

            doc.add(new Paragraph("Department: " + department));
            doc.add(new Paragraph("Programme: " + programme));
            doc.add(new Paragraph("Academic Year: " + academicYear));
            doc.add(new Paragraph("Generated: " + LocalDateTime.now().format(TS)));
            doc.add(new Paragraph(" "));

            Table table = new Table(new float[] {2f, 2.5f, 5f, 5f});
            table.setWidth(UnitValue.createPercentValue(100));
            table.addHeaderCell(new Cell().add(new Paragraph(reportType)));
            table.addHeaderCell(new Cell().add(new Paragraph("Attainment %")));
            table.addHeaderCell(new Cell().add(new Paragraph("Comment")));
            table.addHeaderCell(new Cell().add(new Paragraph("Suggestions")));

            for (AttainmentService.OutcomeAttainmentRow row : rows) {
                ReportCommentInput comment = commentsByCode.get(row.code());
                table.addCell(new Cell().add(new Paragraph(row.code())));
                table.addCell(new Cell().add(new Paragraph(String.format(Locale.US, "%.2f", row.attainedPercent()))));
                table.addCell(new Cell().add(new Paragraph(comment != null && comment.comment() != null ? comment.comment() : "")));
                table.addCell(new Cell().add(new Paragraph(comment != null && comment.suggestions() != null ? comment.suggestions() : "")));
            }
            doc.add(table);
        }

        return fileName;
    }

    // Mirrors AdminReportService.resolveReportDir: prefer the legacy desktop app's own
    // output folders if it's present on the same disk, so reports from both apps show
    // up in one place during the migration period, falling back to a local folder.
    private Path resolveReportDir(String dirName) {
        Path preferred = Paths.get("..", "CO_PO_Assessment", dirName).normalize();
        if (Files.exists(preferred.getParent())) {
            return preferred;
        }
        return Paths.get(dirName).normalize();
    }
}
