package org.example.copo.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@Service
public class AdminReportService {

    public record CohortStudentDto(String id, String name) {}
    public record CohortPoRowDto(String po, double percent) {}
    public record CohortPoReportResultDto(List<CohortPoRowDto> rows, List<String> statusMessages, String pdfFileName) {}
    public record ReportFileDto(String type, String name, long sizeBytes, String lastModified) {}

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JdbcTemplate jdbcTemplate;
    private final ReportStorageService reportStorage;

    public AdminReportService(JdbcTemplate jdbcTemplate, ReportStorageService reportStorage) {
        this.jdbcTemplate = jdbcTemplate;
        this.reportStorage = reportStorage;
    }

    public List<String> getDistinctProgrammesFromStudents() {
        String sql = "SELECT DISTINCT programme FROM Student WHERE programme IS NOT NULL AND programme<>'' ORDER BY programme";
        return jdbcTemplate.query(sql, (rs, n) -> rs.getString(1));
    }

    public List<Integer> getBatchesForProgramme(String programme) {
        String sql = "SELECT DISTINCT batch FROM Student WHERE programme=? ORDER BY batch";
        return jdbcTemplate.query(sql, (rs, n) -> rs.getInt(1), programme);
    }

    public List<CohortStudentDto> getGraduatingCohort(String programme, int batch) {
        String sql = """
            SELECT s.id, s.name
            FROM Student s
            JOIN GraduatingStudent gs ON gs.id = s.id
            WHERE s.programme = ? AND s.batch = ?
            ORDER BY s.id
            """;
        return jdbcTemplate.query(sql, (rs, n) -> new CohortStudentDto(rs.getString("id"), rs.getString("name")), programme, batch);
    }

    public CohortPoReportResultDto generateCohortPoReport(String programme, int batch) {
        List<String> status = new ArrayList<>();

        List<String> studentIds = jdbcTemplate.query(
            """
            SELECT s.id
            FROM Student s
            JOIN GraduatingStudent gs ON gs.id = s.id
            WHERE s.programme = ? AND s.batch = ?
            ORDER BY s.id
            """,
            (rs, n) -> rs.getString(1),
            programme,
            batch
        );

        if (studentIds.isEmpty()) {
            status.add("No graduating students flagged for selected cohort.");
            return new CohortPoReportResultDto(List.of(), status, null);
        }

        List<String> culminationCourses = jdbcTemplate.query(
            "SELECT course_code FROM CulminationCourse WHERE programme=? ORDER BY course_code",
            (rs, n) -> rs.getString(1),
            programme
        );

        if (culminationCourses.isEmpty()) {
            status.add("No culmination courses configured for selected programme.");
            return new CohortPoReportResultDto(List.of(), status, null);
        }

        double poThreshold = getPoThresholdFraction();

        Map<String, Map<String, CourseAssessmentBundle>> courseYearBundle = new HashMap<>();
        Map<String, Integer> poAttainedCounts = new HashMap<>();
        Set<String> allPOsSeen = new TreeSet<>(Comparator.comparingInt(AdminReportService::extractNumber));

        List<String> issues = new ArrayList<>();

        for (String sid : studentIds) {
            boolean hasBlockingIssue = false;
            Set<String> studentAttainedPOs = new HashSet<>();

            for (String courseCode : culminationCourses) {
                String ay = getEnrollmentYear(sid, courseCode, programme);
                if (ay == null) {
                    issues.add("[" + sid + "] Not enrolled in culmination course " + courseCode);
                    hasBlockingIssue = true;
                    continue;
                }

                courseYearBundle.computeIfAbsent(courseCode, k -> new HashMap<>());
                Map<String, CourseAssessmentBundle> byYear = courseYearBundle.get(courseCode);

                CourseAssessmentBundle bundle = byYear.get(ay);
                if (bundle == null) {
                    bundle = buildAssessmentBundle(courseCode, programme, ay, issues);
                    byYear.put(ay, bundle);
                }

                if (bundle.poTotals.isEmpty()) {
                    hasBlockingIssue = true;
                    continue;
                }

                int required = 0;
                int graded = 0;
                Map<String, Double> studentCoursePoObtained = new HashMap<>();

                for (Integer sectionId : bundle.sectionIds) {
                    List<Map<String, Object>> markRows = jdbcTemplate.queryForList(
                        """
                        SELECT aq.id AS question_id, sm.marks_obtained
                        FROM Assessment a
                        JOIN AssessmentQuestion aq ON aq.assessment_id = a.id
                        LEFT JOIN StudentAssessmentMarks sm ON sm.question_id = aq.id AND sm.student_id = ?
                        WHERE a.section_id = ? AND a.academic_year = ?
                        """,
                        sid,
                        sectionId,
                        ay
                    );

                    for (Map<String, Object> row : markRows) {
                        Integer qid = asInt(row.get("question_id"));
                        if (qid == null) continue;

                        List<String> pos = bundle.idToPO.getOrDefault(qid, List.of());
                        if (pos.isEmpty()) continue;

                        // required/graded are counted once per (student, question) -
                        // matching GraduatingCohortPOReportController exactly - not once
                        // per (student, question, PO). A question mapped to 3 POs is
                        // still one mark cell to grade, not three. The obtained marks
                        // still contribute to every PO it's mapped to, just below.
                        required++;
                        Double got = asDouble(row.get("marks_obtained"));
                        if (got != null) {
                            graded++;
                            for (String po : pos) {
                                studentCoursePoObtained.merge(po, got, Double::sum);
                            }
                        }
                    }
                }

                if (required == 0) {
                    issues.add("[" + sid + "] No PO-mapped questions found in course " + courseCode + " (" + ay + ")");
                    hasBlockingIssue = true;
                    continue;
                }

                if (graded < required) {
                    issues.add("[" + sid + "] Not all marks graded in course " + courseCode + " (" + ay + ") [graded " + graded + " / required " + required + "]");
                    hasBlockingIssue = true;
                    continue;
                }

                for (Map.Entry<String, Double> e : bundle.poTotals.entrySet()) {
                    String po = e.getKey();
                    double denom = e.getValue();
                    if (denom <= 0) continue;
                    double got = studentCoursePoObtained.getOrDefault(po, 0.0);
                    if (got / denom >= poThreshold) {
                        studentAttainedPOs.add(po);
                    }
                }

                allPOsSeen.addAll(bundle.poTotals.keySet());
            }

            if (hasBlockingIssue) {
                continue;
            }

            for (String po : studentAttainedPOs) {
                poAttainedCounts.merge(po, 1, Integer::sum);
            }
        }

        if (!issues.isEmpty()) {
            status.add("Validation failed. Resolve following issues:");
            status.addAll(issues);
            return new CohortPoReportResultDto(List.of(), status, null);
        }

        int cohortSize = studentIds.size();
        List<CohortPoRowDto> rows = new ArrayList<>();
        for (String po : allPOsSeen) {
            int attained = poAttainedCounts.getOrDefault(po, 0);
            double pct = (attained * 100.0) / cohortSize;
            rows.add(new CohortPoRowDto(po, pct));
        }

        rows.sort(Comparator.comparingInt(r -> extractNumber(r.po())));

        String fileName = null;
        try {
            fileName = writeCohortPdf(programme, batch, cohortSize, rows);
            status.add("Report generated successfully.");
        } catch (Exception ex) {
            status.add("Failed to save PDF: " + ex.getMessage());
        }

        return new CohortPoReportResultDto(rows, status, fileName);
    }

    public List<ReportFileDto> listReportFiles() {
        List<ReportFileDto> files = new ArrayList<>();
        files.addAll(scanDir(reportStorage.resolveReportDir("co_reports"), "CO"));
        files.addAll(scanDir(reportStorage.resolveReportDir("po_reports"), "PO"));
        files.addAll(scanDir(reportStorage.resolveReportDir("course_reports"), "Course"));
        files.addAll(scanDir(reportStorage.resolveReportDir("summary_reports"), "Summary"));
        files.addAll(scanDir(reportStorage.resolveReportDir("detailed_marks_reports"), "Detailed"));
        files.addAll(scanDir(reportStorage.resolveReportDir("consolidated_marks_reports"), "Consolidated"));
        files.addAll(scanDir(reportStorage.resolveReportDir("cohort_po_reports"), "Cohort PO"));
        files.sort(Comparator.comparing(ReportFileDto::type).thenComparing(ReportFileDto::name));
        return files;
    }

    public File resolveDownloadFile(String type, String name) {
        Path dir = switch (type) {
            case "CO" -> reportStorage.resolveReportDir("co_reports");
            case "PO" -> reportStorage.resolveReportDir("po_reports");
            case "Course" -> reportStorage.resolveReportDir("course_reports");
            case "Summary" -> reportStorage.resolveReportDir("summary_reports");
            case "Detailed" -> reportStorage.resolveReportDir("detailed_marks_reports");
            case "Consolidated" -> reportStorage.resolveReportDir("consolidated_marks_reports");
            case "Cohort PO" -> reportStorage.resolveReportDir("cohort_po_reports");
            default -> null;
        };

        if (dir == null) return null;

        Path file = dir.resolve(name).normalize();
        if (!file.startsWith(dir) || !Files.exists(file) || !Files.isRegularFile(file)) {
            return null;
        }
        return file.toFile();
    }

    // Every report used to be a PDF, so both download endpoints hardcoded that content
    // type - Consolidated Marks Report is the first .xlsx, so downloads need to pick
    // the right one instead of mislabeling a spreadsheet as a PDF.
    public static String contentTypeFor(File file) {
        String lower = file.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        return "application/pdf";
    }

    private CourseAssessmentBundle buildAssessmentBundle(String courseCode, String programme, String academicYear, List<String> issues) {
        List<Map<String, Object>> sectionRows = jdbcTemplate.queryForList(
            "SELECT id, display_name FROM CourseAssessmentSection WHERE course_code=? AND programme=? ORDER BY section_order, id",
            courseCode,
            programme
        );

        List<Integer> sectionIds = new ArrayList<>();
        List<String> missingSections = new ArrayList<>();
        List<String> noPOSections = new ArrayList<>();

        Map<String, Double> poTotals = new HashMap<>();
        Map<Integer, List<String>> idToPO = new HashMap<>();

        for (Map<String, Object> section : sectionRows) {
            Integer sectionId = asInt(section.get("id"));
            String sectionName = Objects.toString(section.get("display_name"), "Section");
            if (sectionId == null) continue;

            sectionIds.add(sectionId);

            // Two separate queries (questions, then their PO mappings) rather than one
            // join, so a section that has questions but none PO-mapped can still be told
            // apart from a section with no questions at all - a plain join through
            // AssessmentQuestion_PO would simply drop unmapped questions from the result.
            List<Map<String, Object>> qRows = jdbcTemplate.queryForList(
                """
                SELECT aq.id AS question_id, aq.marks AS marks
                FROM Assessment a
                JOIN AssessmentQuestion aq ON aq.assessment_id = a.id
                WHERE a.section_id = ? AND a.academic_year = ?
                """,
                sectionId,
                academicYear
            );

            if (qRows.isEmpty()) {
                missingSections.add(sectionName);
                continue;
            }

            Map<Integer, Double> questionMarks = new HashMap<>();
            List<Integer> questionIds = new ArrayList<>();
            for (Map<String, Object> q : qRows) {
                Integer qid = asInt(q.get("question_id"));
                if (qid == null) continue;
                questionIds.add(qid);
                questionMarks.put(qid, asDouble(q.get("marks")));
            }

            String placeholders = String.join(",", java.util.Collections.nCopies(questionIds.size(), "?"));
            List<Map<String, Object>> poRows = questionIds.isEmpty() ? List.of() : jdbcTemplate.queryForList(
                "SELECT aqp.question_id AS question_id, po.po_number AS po_number " +
                    "FROM AssessmentQuestion_PO aqp JOIN PO po ON aqp.po_id = po.id " +
                    "WHERE aqp.question_id IN (" + placeholders + ")",
                questionIds.toArray()
            );

            boolean hasPo = false;
            for (Map<String, Object> row : poRows) {
                Integer qid = asInt(row.get("question_id"));
                String po = Objects.toString(row.get("po_number"), "").trim().toUpperCase(Locale.ROOT);
                if (qid == null || po.isBlank()) continue;

                hasPo = true;
                Double marks = questionMarks.get(qid);
                poTotals.merge(po, marks == null ? 0.0 : marks, Double::sum);
                idToPO.computeIfAbsent(qid, k -> new ArrayList<>()).add(po);
            }

            if (!hasPo) {
                noPOSections.add(sectionName);
            }
        }

        if (!missingSections.isEmpty()) {
            issues.add("Course " + courseCode + " (" + academicYear + ") has no questions for: " + String.join(", ", missingSections));
        }
        if (!noPOSections.isEmpty()) {
            issues.add("Course " + courseCode + " (" + academicYear + ") has questions but none mapped to POs for: " + String.join(", ", noPOSections));
        }

        return new CourseAssessmentBundle(sectionIds, poTotals, idToPO);
    }

    private String writeCohortPdf(String programme, int batch, int cohortSize, List<CohortPoRowDto> rows) throws Exception {
        Path dir = reportStorage.resolveReportDir("cohort_po_reports");
        Files.createDirectories(dir);

        String safeProgramme = programme.replaceAll("[^A-Za-z0-9_-]", "");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
        String fileName = "CohortPO_" + safeProgramme + "_Batch" + batch + "_" + timestamp + ".pdf";
        File outFile = dir.resolve(fileName).toFile();

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(outFile));
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf)) {

            Paragraph title = new Paragraph("Graduating Cohort PO Report")
                .setFontSize(16)
                .setTextAlignment(TextAlignment.CENTER);
            doc.add(title);

            doc.add(new Paragraph("Programme: " + programme));
            doc.add(new Paragraph("Batch: " + batch));
            doc.add(new Paragraph("Cohort size: " + cohortSize));
            doc.add(new Paragraph("Generated on: " + LocalDateTime.now().format(TS)));
            doc.add(new Paragraph(" "));

            Table table = new Table(new float[] {2f, 3f});
            table.setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(60));
            table.addHeaderCell(new Cell().add(new Paragraph("PO")));
            table.addHeaderCell(new Cell().add(new Paragraph("Attainment %")));

            for (CohortPoRowDto row : rows) {
                table.addCell(new Cell().add(new Paragraph(row.po())));
                table.addCell(new Cell().add(new Paragraph(String.format(Locale.US, "%.2f", row.percent()))));
            }

            doc.add(table);
        }

        return fileName;
    }

    private List<ReportFileDto> scanDir(Path dir, String type) {
        List<ReportFileDto> list = new ArrayList<>();
        if (!Files.exists(dir)) return list;

        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                .filter(p -> {
                    String lower = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    return lower.endsWith(".pdf") || lower.endsWith(".xlsx");
                })
                .forEach(p -> {
                    try {
                        long size = Files.size(p);
                        String modified = TS.format(Files.getLastModifiedTime(p).toInstant().atZone(java.time.ZoneId.systemDefault()));
                        list.add(new ReportFileDto(type, p.getFileName().toString(), size, modified));
                    } catch (Exception ignored) {
                    }
                });
        } catch (Exception ignored) {
        }

        return list;
    }

    private String getEnrollmentYear(String studentId, String courseCode, String programme) {
        List<String> years = jdbcTemplate.query(
            "SELECT academic_year FROM Enrollment WHERE student_id=? AND course_id=? AND programme=? ORDER BY academic_year DESC LIMIT 1",
            (rs, n) -> rs.getString(1),
            studentId,
            courseCode,
            programme
        );
        return years.isEmpty() ? null : years.get(0);
    }

    private double getPoThresholdFraction() {
        List<Double> v = jdbcTemplate.query(
            "SELECT percentage FROM Thresholds WHERE type='PO_INDIVIDUAL'",
            (rs, n) -> rs.getDouble(1)
        );
        if (v.isEmpty()) return 0.40;
        return Math.max(0, Math.min(100, v.get(0))) / 100.0;
    }

    private static int extractNumber(String label) {
        try {
            return Integer.parseInt(label.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    private static Integer asInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception ex) {
            return null;
        }
    }

    private static Double asDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception ex) {
            return null;
        }
    }

    private record CourseAssessmentBundle(List<Integer> sectionIds, Map<String, Double> poTotals, Map<Integer, List<String>> idToPO) {
    }
}
