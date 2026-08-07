package org.example.copo.service;

import lombok.RequiredArgsConstructor;
import org.example.copo.entity.Course;
import org.example.copo.entity.CourseAssignment;
import org.example.copo.entity.Enrollment;
import org.example.copo.entity.Faculty;
import org.example.copo.entity.Student;
import org.example.copo.repository.CORepository;
import org.example.copo.repository.CourseRepository;
import org.example.copo.repository.EnrollmentRepository;
import org.example.copo.repository.FacultyRepository;
import org.example.copo.repository.PORepository;
import org.example.copo.repository.StudentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ports the desktop app's five "Bulk Import" buttons (Students, Faculties, Courses,
 * Enrollments, Course Assignments) - each admin screen had its own onBulkImport
 * handler built on the shared ExcelImportUtils reader; this collapses them into one
 * service, one method per entity, all sharing the same "per-row try/catch, collect an
 * error message and keep going" shape the desktop version used rather than aborting
 * the whole file on the first bad row.
 *
 * One deliberate behavior difference from desktop: Faculty.shortname is a required
 * (@NotBlank) column in this schema, whereas desktop silently defaulted a missing
 * shortname to an empty string. A blank shortname is now a validation error for that
 * row instead - the web schema's stricter validation (from phase 0.1) applies
 * consistently here too rather than being bypassed for bulk import specifically.
 */
@Service
@RequiredArgsConstructor
public class BulkImportService {

    private static final List<String> DEFAULT_SECTIONS = List.of("Quiz 1", "Quiz 2", "Quiz 3", "Quiz 4", "Mid", "Final");

    private final StudentRepository studentRepository;
    private final FacultyRepository facultyRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CORepository coRepository;
    private final PORepository poRepository;
    private final CourseOutcomeService courseOutcomeService;
    private final CourseAssessmentSectionService sectionService;
    private final CourseAssignmentService courseAssignmentService;
    private final PasswordEncoder passwordEncoder;

    public record ImportResult(int inserted, int skipped, int mapped, List<String> errors) {}

    public ImportResult importStudents(MultipartFile file) throws IOException {
        List<Map<String, String>> rows = ExcelImportUtils.readSheetAsMaps(file.getInputStream());
        List<String> errors = new ArrayList<>();
        int inserted = 0, skipped = 0;
        int rowNum = 1;
        for (Map<String, String> row : rows) {
            rowNum++;
            String id = ExcelImportUtils.get(row, "id", "student_id");
            String name = ExcelImportUtils.get(row, "name", "full_name");
            String batchStr = ExcelImportUtils.get(row, "batch", "year");
            String email = ExcelImportUtils.get(row, "email");
            String dept = ExcelImportUtils.get(row, "department", "dept");
            String prog = ExcelImportUtils.get(row, "programme", "program");

            if (id == null || name == null || batchStr == null || dept == null || prog == null) {
                errors.add("Row " + rowNum + ": missing required fields (id, name, batch, department, programme)");
                skipped++;
                continue;
            }
            Integer batch = parseInt(batchStr);
            if (batch == null) {
                errors.add("Row " + rowNum + ": invalid batch '" + batchStr + "'");
                skipped++;
                continue;
            }

            Student student = new Student();
            student.setId(id);
            student.setBatch(batch);
            student.setName(name);
            student.setEmail(email);
            student.setDepartment(dept);
            student.setProgramme(prog);
            try {
                studentRepository.save(student);
                inserted++;
            } catch (DataIntegrityViolationException ex) {
                errors.add("Row " + rowNum + ": duplicate (" + id + ")");
                skipped++;
            } catch (Exception ex) {
                errors.add("Row " + rowNum + ": " + ex.getMessage());
                skipped++;
            }
        }
        return new ImportResult(inserted, skipped, 0, errors);
    }

    public ImportResult importFaculties(MultipartFile file) throws IOException {
        List<Map<String, String>> rows = ExcelImportUtils.readSheetAsMaps(file.getInputStream());
        List<String> errors = new ArrayList<>();
        int inserted = 0, skipped = 0;
        int rowNum = 1;
        for (Map<String, String> row : rows) {
            rowNum++;
            String id = ExcelImportUtils.get(row, "id", "faculty_id");
            String shortname = ExcelImportUtils.get(row, "shortname", "short_name");
            String fullName = ExcelImportUtils.get(row, "full_name", "name", "faculty_name");
            String email = ExcelImportUtils.get(row, "email");
            String password = ExcelImportUtils.get(row, "password");

            if (id == null || fullName == null || email == null || shortname == null) {
                errors.add("Row " + rowNum + ": missing required fields (id, shortname, full_name/name, email)");
                skipped++;
                continue;
            }
            if (password == null) {
                password = (fullName.split("\\s+")[0] + "@" + id).toLowerCase(Locale.ROOT);
            }

            Faculty faculty = new Faculty();
            faculty.setId(id);
            faculty.setShortname(shortname);
            faculty.setFullName(fullName);
            faculty.setEmail(email);
            faculty.setPassword(passwordEncoder.encode(password));
            try {
                facultyRepository.save(faculty);
                inserted++;
            } catch (DataIntegrityViolationException ex) {
                errors.add("Row " + rowNum + ": duplicate (" + id + ")");
                skipped++;
            } catch (Exception ex) {
                errors.add("Row " + rowNum + ": " + ex.getMessage());
                skipped++;
            }
        }
        return new ImportResult(inserted, skipped, 0, errors);
    }

    public ImportResult importCourses(MultipartFile file) throws IOException {
        List<Map<String, String>> rows = ExcelImportUtils.readSheetAsMaps(file.getInputStream());
        List<String> errors = new ArrayList<>();
        int inserted = 0, skipped = 0, mapped = 0;

        Map<String, Integer> coIdByNumber = numberToIdMap(coRepository.findAll().stream().map(co -> Map.entry(co.getCoNumber(), co.getId())));
        Map<String, Integer> poIdByNumber = numberToIdMap(poRepository.findAll().stream().map(po -> Map.entry(po.getPoNumber(), po.getId())));

        int rowNum = 1;
        for (Map<String, String> row : rows) {
            rowNum++;
            String code = ExcelImportUtils.get(row, "course_code", "code");
            String name = ExcelImportUtils.get(row, "course_name", "name", "title");
            String creditsStr = ExcelImportUtils.get(row, "credits", "credit");
            String dept = ExcelImportUtils.get(row, "department", "dept");
            String prog = ExcelImportUtils.get(row, "programme", "program", "program_name");
            String cosRaw = ExcelImportUtils.get(row, "cos", "course_outcomes");
            String posRaw = ExcelImportUtils.get(row, "pos", "program_outcomes", "po_s");
            String sectionsRaw = ExcelImportUtils.get(row, "assessment_sections", "sections", "assessments");

            if (code == null || name == null || creditsStr == null || dept == null || prog == null) {
                errors.add("Row " + rowNum + ": missing required fields (course_code, course_name, credits, department, programme)");
                skipped++;
                continue;
            }
            Double credits = parseDouble(creditsStr);
            if (credits == null) {
                errors.add("Row " + rowNum + ": invalid credits '" + creditsStr + "'");
                skipped++;
                continue;
            }

            List<Integer> coIds = new ArrayList<>();
            String coError = parseOutcomeIds(cosRaw, 20, "CO", coIdByNumber, coIds);
            if (coError != null) errors.add("Row " + rowNum + ": " + coError);
            List<Integer> poIds = new ArrayList<>();
            String poError = parseOutcomeIds(posRaw, 12, "PO", poIdByNumber, poIds);
            if (poError != null) errors.add("Row " + rowNum + ": " + poError);

            Course course = new Course();
            course.setCourseCode(code);
            course.setProgramme(prog);
            course.setCourseName(name);
            course.setCredits(credits);
            course.setDepartment(dept);
            boolean isNew = true;
            try {
                courseRepository.save(course);
                inserted++;
            } catch (DataIntegrityViolationException ex) {
                isNew = false;
            } catch (Exception ex) {
                errors.add("Row " + rowNum + ": " + ex.getMessage());
                skipped++;
                continue;
            }

            try {
                if (!coIds.isEmpty() || !poIds.isEmpty()) {
                    courseOutcomeService.updateCourseOutcomes(code, prog, coIds, poIds);
                    mapped++;
                }
                List<String> sectionNames = parseAssessmentSections(sectionsRaw);
                if (isNew && !sectionNames.isEmpty()) {
                    int order = 1;
                    for (String sectionName : sectionNames) {
                        sectionService.createSection(code, prog, sectionName, order++);
                    }
                    mapped++;
                }
            } catch (Exception ex) {
                errors.add("Row " + rowNum + ": failed to map CO/POs/Sections - " + ex.getMessage());
            }
        }
        return new ImportResult(inserted, skipped, mapped, errors);
    }

    public ImportResult importEnrollments(
        MultipartFile file, String defaultCourseCode, String defaultProgramme, String defaultAcademicYear
    ) throws IOException {
        List<Map<String, String>> rows = ExcelImportUtils.readSheetAsMaps(file.getInputStream());
        List<String> errors = new ArrayList<>();
        int inserted = 0, skipped = 0;
        int rowNum = 1;
        for (Map<String, String> row : rows) {
            rowNum++;
            String sid = ExcelImportUtils.get(row, "student_id", "id");
            String code = orElse(ExcelImportUtils.get(row, "course_code", "course"), defaultCourseCode);
            String prog = orElse(ExcelImportUtils.get(row, "programme", "program"), defaultProgramme);
            String year = orElse(ExcelImportUtils.get(row, "academic_year", "year", "ay"), defaultAcademicYear);

            if (sid == null || code == null || prog == null || year == null) {
                errors.add("Row " + rowNum + ": missing student_id/course_code/programme/academic_year");
                skipped++;
                continue;
            }
            if (!isValidAcademicYear(year)) {
                errors.add("Row " + rowNum + ": invalid academic year '" + year + "' (use YYYY-YYYY like 2024-2025)");
                skipped++;
                continue;
            }

            Enrollment enrollment = new Enrollment();
            enrollment.setStudentId(sid);
            enrollment.setCourseId(code);
            enrollment.setProgramme(prog);
            enrollment.setAcademicYear(year);
            try {
                enrollmentRepository.save(enrollment);
                inserted++;
            } catch (DataIntegrityViolationException ex) {
                errors.add("Row " + rowNum + ": duplicate enrollment (" + sid + ", " + code + ")");
                skipped++;
            } catch (Exception ex) {
                errors.add("Row " + rowNum + ": " + ex.getMessage());
                skipped++;
            }
        }
        return new ImportResult(inserted, skipped, 0, errors);
    }

    public ImportResult importCourseAssignments(MultipartFile file) throws IOException {
        List<Map<String, String>> rows = ExcelImportUtils.readSheetAsMaps(file.getInputStream());
        List<String> errors = new ArrayList<>();
        int inserted = 0, skipped = 0;
        int rowNum = 1;
        for (Map<String, String> row : rows) {
            rowNum++;
            String code = ExcelImportUtils.get(row, "course_code", "course", "code");
            String programme = ExcelImportUtils.get(row, "programme", "program");
            String facultyShort = ExcelImportUtils.get(row, "faculty_shortname", "shortname");
            String facultyName = ExcelImportUtils.get(row, "faculty_name", "faculty", "instructor", "teacher");
            String ay = ExcelImportUtils.get(row, "academic_year", "year", "ay");

            if (code == null || programme == null || ay == null || (facultyShort == null && facultyName == null)) {
                errors.add("Row " + rowNum + ": missing required fields (course_code, programme, faculty_shortname/faculty_name, academic_year)");
                skipped++;
                continue;
            }
            if (!isValidAcademicYear(ay)) {
                errors.add("Row " + rowNum + ": invalid academic year '" + ay + "' (must be consecutive like 2023-2024)");
                skipped++;
                continue;
            }

            Faculty faculty = facultyShort != null
                ? facultyRepository.findByShortname(facultyShort).orElse(null)
                : facultyRepository.findAll().stream().filter(f -> f.getFullName().equalsIgnoreCase(facultyName)).findFirst().orElse(null);
            if (faculty == null) {
                errors.add("Row " + rowNum + ": faculty not found (" + (facultyShort != null ? facultyShort : facultyName) + ")");
                skipped++;
                continue;
            }

            Course course = courseRepository.findById(new Course.CourseId(code, programme)).orElse(null);
            if (course == null) {
                errors.add("Row " + rowNum + ": course not found (" + code + ", " + programme + ")");
                skipped++;
                continue;
            }

            CourseAssignment assignment = new CourseAssignment();
            assignment.setFacultyId(faculty.getId());
            assignment.setCourseCode(code);
            assignment.setProgramme(programme);
            assignment.setAcademicYear(ay);
            assignment.setDepartment(course.getDepartment());
            try {
                courseAssignmentService.createAssignment(assignment);
                inserted++;
            } catch (DataIntegrityViolationException ex) {
                errors.add("Row " + rowNum + ": duplicate for (" + code + ", " + programme + ", " + ay + ")");
                skipped++;
            } catch (Exception ex) {
                errors.add("Row " + rowNum + ": " + ex.getMessage());
                skipped++;
            }
        }
        return new ImportResult(inserted, skipped, 0, errors);
    }

    // ============================== helpers ==============================

    private Map<String, Integer> numberToIdMap(java.util.stream.Stream<Map.Entry<String, Integer>> entries) {
        Map<String, Integer> map = new HashMap<>();
        entries.forEach(e -> map.put(e.getKey(), e.getValue()));
        return map;
    }

    // Accepts "CO1, CO3" or "1,3" (prefix optional, case-insensitive, comma/space/
    // semicolon separated) - same normalization desktop's parseAndValidateOutcomes used.
    private String parseOutcomeIds(String raw, int maxAllowed, String prefix, Map<String, Integer> idByNumber, List<Integer> outIds) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim().replaceAll("\\s+", " ").replaceAll("(?i)" + prefix, "");
        String[] parts = s.split("[ ,;]+");
        List<String> invalid = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) continue;
            Integer number = parseInt(part.trim());
            if (number == null || number < 1 || number > maxAllowed) {
                invalid.add(part);
                continue;
            }
            Integer id = idByNumber.get(prefix + number);
            if (id == null) {
                invalid.add(part);
                continue;
            }
            outIds.add(id);
        }
        return invalid.isEmpty() ? null : "invalid " + prefix + " value(s): " + String.join(", ", invalid);
    }

    private List<String> parseAssessmentSections(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT_SECTIONS;
        List<String> sections = new ArrayList<>();
        for (String part : raw.split(",")) {
            String name = part.trim();
            if (!name.isEmpty()) sections.add(name);
        }
        return sections.isEmpty() ? DEFAULT_SECTIONS : sections;
    }

    private boolean isValidAcademicYear(String academicYear) {
        if (academicYear == null) return false;
        String s = academicYear.trim();
        if (!s.matches("\\d{4}-\\d{4}")) return false;
        try {
            int y1 = Integer.parseInt(s.substring(0, 4));
            int y2 = Integer.parseInt(s.substring(5, 9));
            return y2 == y1 + 1;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private Double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private String orElse(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
