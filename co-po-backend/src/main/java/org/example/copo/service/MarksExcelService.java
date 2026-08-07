package org.example.copo.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.copo.entity.Assessment;
import org.example.copo.entity.AssessmentQuestion;
import org.example.copo.entity.Course;
import org.example.copo.entity.CourseAssessmentSection;
import org.example.copo.repository.AssessmentQuestionRepository;
import org.example.copo.repository.AssessmentRepository;
import org.example.copo.repository.CourseAssessmentSectionRepository;
import org.example.copo.repository.CourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Ports the desktop app's "Export Marks" / "Import Marks" sidebar buttons
 * (FacultyDashboardController.onExcelExportButton/onExcelImportButton) - a full
 * course roster, one worksheet per assessment section plus (for legacy, pre-batch-23
 * offerings) an Attendance sheet, exported pre-filled with whatever marks already
 * exist so a faculty member can grade offline and bring the file back.
 *
 * Deliberately thin: every read and every write goes through GradebookService and
 * AttendanceService (phases 3.1/3.3) rather than querying marks/attendance directly -
 * this only owns the Excel shape (sheets, columns, formulas, validation) and the
 * sheet/column-header matching needed to round-trip it, not a second copy of the
 * persistence or authorization logic those services already have.
 */
@Service
@RequiredArgsConstructor
public class MarksExcelService {

    private static final String ATTENDANCE_SHEET_NAME = "Attendance";
    private static final String MAX_MARKER = " (Max: ";

    private final CourseAssessmentSectionRepository sectionRepository;
    private final AssessmentRepository assessmentRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final CourseRepository courseRepository;
    private final GradebookService gradebookService;
    private final AttendanceService attendanceService;
    private final AssignmentAuthorizationService authorizationService;

    public record MarksImportResult(int marksSaved, int attendanceSaved, List<String> errors) {}

    public byte[] exportMarks(String facultyEmail, String courseCode, String programme, String academicYear) throws IOException {
        authorizationService.requireAssignedToCourse(facultyEmail, courseCode, programme, academicYear);

        List<CourseAssessmentSection> sections = sectionRepository.findByCourseCodeAndProgrammeOrderBySectionOrderAsc(courseCode, programme);
        Course course = courseRepository.findById(new Course.CourseId(courseCode, programme)).orElse(null);
        double credits = (course != null && course.getCredits() != null) ? course.getCredits() : 0.0;

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (CourseAssessmentSection section : sections) {
                Optional<Assessment> assessment = assessmentRepository.findBySectionIdAndAcademicYear(section.getId(), academicYear);
                if (assessment.isEmpty()) continue;
                List<AssessmentQuestion> questions = questionRepository.findByAssessmentId(assessment.get().getId());
                if (questions.isEmpty()) continue;

                GradebookService.RosterDto roster = gradebookService.getRoster(facultyEmail, assessment.get().getId());
                writeSectionSheet(workbook, section.getDisplayName(), roster);
            }

            AttendanceService.AttendanceStatusDto attendance = attendanceService.getAttendanceStatus(facultyEmail, courseCode, programme, academicYear);
            if (attendance.legacyOffering()) {
                writeAttendanceSheet(workbook, attendance, credits);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    public MarksImportResult importMarks(
        String facultyEmail, String courseCode, String programme, String academicYear, MultipartFile file
    ) throws IOException {
        authorizationService.requireAssignedToCourse(facultyEmail, courseCode, programme, academicYear);

        Map<String, CourseAssessmentSection> sectionByName = new HashMap<>();
        for (CourseAssessmentSection section : sectionRepository.findByCourseCodeAndProgrammeOrderBySectionOrderAsc(courseCode, programme)) {
            sectionByName.put(section.getDisplayName().toLowerCase(Locale.ROOT), section);
        }
        AttendanceService.AttendanceStatusDto attendanceStatus = attendanceService.getAttendanceStatus(facultyEmail, courseCode, programme, academicYear);

        List<String> errors = new ArrayList<>();
        int marksSaved = 0, attendanceSaved = 0;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName();

                if (ATTENDANCE_SHEET_NAME.equalsIgnoreCase(sheetName)) {
                    if (attendanceStatus.legacyOffering()) {
                        attendanceSaved += importAttendanceSheet(facultyEmail, courseCode, programme, academicYear, sheet, errors);
                    }
                    continue;
                }

                CourseAssessmentSection section = sectionByName.get(sheetName.toLowerCase(Locale.ROOT));
                if (section == null) {
                    errors.add("Sheet \"" + sheetName + "\": no matching assessment section for this course.");
                    continue;
                }
                Optional<Assessment> assessment = assessmentRepository.findBySectionIdAndAcademicYear(section.getId(), academicYear);
                if (assessment.isEmpty()) {
                    errors.add("Sheet \"" + sheetName + "\": no questions configured for " + academicYear + " yet.");
                    continue;
                }
                marksSaved += importSectionSheet(facultyEmail, assessment.get().getId(), sheet, errors);
            }
        }

        return new MarksImportResult(marksSaved, attendanceSaved, errors);
    }

    // ============================== export helpers ==============================

    private void writeSectionSheet(Workbook workbook, String sectionName, GradebookService.RosterDto roster) {
        Sheet sheet = workbook.createSheet(safeSheetName(sectionName));
        CellStyle headerStyle = headerStyle(workbook);

        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Student ID");
        header.createCell(1).setCellValue("Student Name");
        int col = 2;
        for (GradebookService.RosterQuestionDto q : roster.questions()) {
            Cell cell = header.createCell(col++);
            cell.setCellValue(q.title() + MAX_MARKER + fmt(q.maxMarks()) + ")");
            cell.setCellStyle(headerStyle);
        }
        Cell totalHeader = header.createCell(col);
        totalHeader.setCellValue("Total");
        totalHeader.setCellStyle(headerStyle);
        for (int c = 0; c <= col; c++) header.getCell(c).setCellStyle(headerStyle);

        DataValidationHelper validationHelper = sheet.getDataValidationHelper();
        int questionCount = roster.questions().size();

        int rowIdx = 1;
        for (GradebookService.RosterRowDto studentRow : roster.rows()) {
            Row row = sheet.createRow(rowIdx);
            row.createCell(0).setCellValue(studentRow.studentId());
            row.createCell(1).setCellValue(studentRow.studentName());
            int qCol = 2;
            for (GradebookService.RosterCellDto cellDto : studentRow.cells()) {
                Cell cell = row.createCell(qCol);
                if (cellDto.marksObtained() != null) cell.setCellValue(cellDto.marksObtained());
                qCol++;
            }
            if (questionCount > 0) {
                String firstCol = CellReference.convertNumToColString(2);
                String lastCol = CellReference.convertNumToColString(2 + questionCount - 1);
                row.createCell(2 + questionCount).setCellFormula("SUM(" + firstCol + (rowIdx + 1) + ":" + lastCol + (rowIdx + 1) + ")");
            }
            rowIdx++;
        }

        for (int i = 0; i < questionCount; i++) {
            double max = roster.questions().get(i).maxMarks();
            addNumericValidation(sheet, validationHelper, 2 + i, 1, rowIdx - 1, 0, max);
        }
        for (int c = 0; c <= col; c++) sheet.autoSizeColumn(c);
    }

    private void writeAttendanceSheet(Workbook workbook, AttendanceService.AttendanceStatusDto attendance, double credits) {
        Sheet sheet = workbook.createSheet(ATTENDANCE_SHEET_NAME);
        CellStyle headerStyle = headerStyle(workbook);

        Row header = sheet.createRow(0);
        String[] headers = {"Student ID", "Student Name", "Attendance %", "Attendance Marks"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        DataValidationHelper validationHelper = sheet.getDataValidationHelper();
        int rowIdx = 1;
        for (AttendanceService.AttendanceRowDto row : attendance.rows()) {
            Row r = sheet.createRow(rowIdx);
            r.createCell(0).setCellValue(row.studentId());
            r.createCell(1).setCellValue(row.studentName());
            if (row.attendancePercentage() != null) r.createCell(2).setCellValue(row.attendancePercentage());
            r.createCell(3).setCellFormula(buildAttendanceMarksFormula(rowIdx + 1, credits));
            rowIdx++;
        }

        addNumericValidation(sheet, validationHelper, 2, 1, rowIdx - 1, 0, 100);
        for (int c = 0; c < headers.length; c++) sheet.autoSizeColumn(c);
    }

    // Same credits-based bucket rule as CourseResultService.calculateAttendanceRawMarks
    // (>=95% -> credits*10, >=90% -> credits*8, ... else 0), written as an Excel
    // formula instead of Java so the exported sheet shows the derived value live as
    // faculty fill in %, exactly like the desktop version's export did.
    private String buildAttendanceMarksFormula(int rowNumber, double credits) {
        String pctRef = "C" + rowNumber;
        String creditsLiteral = String.valueOf(credits);
        return "IF(" + pctRef + "=\"\",\"\",IF(" + pctRef + ">=95," + creditsLiteral + "*10,"
            + "IF(" + pctRef + ">=90," + creditsLiteral + "*8,"
            + "IF(" + pctRef + ">=85," + creditsLiteral + "*6,"
            + "IF(" + pctRef + ">=80," + creditsLiteral + "*4,"
            + "IF(" + pctRef + ">=75," + creditsLiteral + "*2,0)))))";
    }

    private void addNumericValidation(Sheet sheet, DataValidationHelper helper, int col, int firstRow, int lastRow, double min, double max) {
        if (lastRow < firstRow) return;
        CellRangeAddressList range = new CellRangeAddressList(firstRow, lastRow, col, col);
        DataValidationConstraint constraint = helper.createDecimalConstraint(
            DataValidationConstraint.OperatorType.BETWEEN, String.valueOf(min), String.valueOf(max)
        );
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setShowErrorBox(true);
        validation.createErrorBox("Invalid marks", "Value must be between " + fmt(min) + " and " + fmt(max) + ".");
        sheet.addValidationData(validation);
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private String safeSheetName(String name) {
        String cleaned = WorkbookUtil.createSafeSheetName(name);
        return ATTENDANCE_SHEET_NAME.equalsIgnoreCase(cleaned) ? cleaned + " " : cleaned;
    }

    private String fmt(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    // ============================== import helpers ==============================

    private int importSectionSheet(String facultyEmail, Integer assessmentId, Sheet sheet, List<String> errors) {
        List<AssessmentQuestion> questions = questionRepository.findByAssessmentId(assessmentId);
        Map<String, AssessmentQuestion> questionByTitle = new HashMap<>();
        for (AssessmentQuestion q : questions) {
            questionByTitle.put(q.getTitle().toLowerCase(Locale.ROOT), q);
        }

        DataFormatter formatter = new DataFormatter();
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) return 0;

        Map<Integer, AssessmentQuestion> questionByColumn = new HashMap<>();
        for (int c = 2; c < headerRow.getLastCellNum(); c++) {
            Cell cell = headerRow.getCell(c);
            if (cell == null) continue;
            String header = formatter.formatCellValue(cell);
            String title = header.contains(MAX_MARKER) ? header.substring(0, header.indexOf(MAX_MARKER)).trim() : header.trim();
            AssessmentQuestion question = questionByTitle.get(title.toLowerCase(Locale.ROOT));
            if (question != null) questionByColumn.put(c, question);
        }

        List<GradebookService.MarkEntry> entries = new ArrayList<>();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Cell idCell = row.getCell(0);
            String studentId = idCell == null ? "" : formatter.formatCellValue(idCell).trim();
            if (studentId.isBlank()) continue;

            for (Map.Entry<Integer, AssessmentQuestion> entry : questionByColumn.entrySet()) {
                Cell cell = row.getCell(entry.getKey());
                String raw = cell == null ? "" : formatter.formatCellValue(cell).trim();
                if (raw.isBlank()) continue;
                Double marks = parseDouble(raw);
                AssessmentQuestion question = entry.getValue();
                if (marks == null || marks < 0 || marks > question.getMarks()) {
                    errors.add("Row " + (r + 1) + " (" + studentId + "), \"" + question.getTitle() + "\": invalid marks '" + raw + "'.");
                    continue;
                }
                entries.add(new GradebookService.MarkEntry(studentId, question.getId(), marks));
            }
        }

        if (entries.isEmpty()) return 0;
        GradebookService.BatchSaveResult result = gradebookService.saveBatch(
            facultyEmail, assessmentId, new GradebookService.BatchSaveRequest(entries)
        );
        errors.addAll(result.errors());
        return result.saved();
    }

    private int importAttendanceSheet(
        String facultyEmail, String courseCode, String programme, String academicYear, Sheet sheet, List<String> errors
    ) {
        DataFormatter formatter = new DataFormatter();
        List<AttendanceService.AttendanceEntry> entries = new ArrayList<>();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Cell idCell = row.getCell(0);
            String studentId = idCell == null ? "" : formatter.formatCellValue(idCell).trim();
            if (studentId.isBlank()) continue;

            Cell pctCell = row.getCell(2);
            String raw = pctCell == null ? "" : formatter.formatCellValue(pctCell).trim();
            if (raw.isBlank()) continue;
            Double pct = parseDouble(raw);
            if (pct == null || pct < 0 || pct > 100) {
                errors.add("Attendance row " + (r + 1) + " (" + studentId + "): invalid attendance % '" + raw + "'.");
                continue;
            }
            entries.add(new AttendanceService.AttendanceEntry(studentId, pct));
        }

        if (entries.isEmpty()) return 0;
        AttendanceService.AttendanceSaveResult result = attendanceService.saveAttendance(
            facultyEmail, courseCode, programme, academicYear, new AttendanceService.AttendanceSaveRequest(entries)
        );
        errors.addAll(result.errors());
        return result.saved();
    }

    private Double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }
}
