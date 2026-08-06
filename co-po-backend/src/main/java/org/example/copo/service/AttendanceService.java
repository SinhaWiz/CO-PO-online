package org.example.copo.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.example.copo.entity.Enrollment;
import org.example.copo.entity.EnrollmentAttendance;
import org.example.copo.entity.Student;
import org.example.copo.repository.EnrollmentAttendanceRepository;
import org.example.copo.repository.EnrollmentRepository;
import org.example.copo.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Attendance only matters for "legacy" course offerings - the desktop app's rule is
 * that a course counts as legacy if the majority of its enrolled students' batch
 * number is under 23 (ties broken toward the lower batch). Legacy offerings weight
 * attendance into the final result (Phase 5.3); everyone else skips it entirely.
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final EnrollmentAttendanceRepository attendanceRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final AssignmentAuthorizationService authorizationService;

    public record AttendanceRowDto(String studentId, String studentName, Double attendancePercentage) {}
    public record AttendanceStatusDto(boolean legacyOffering, Integer majorityBatch, List<AttendanceRowDto> rows) {}

    public record AttendanceEntry(
        @NotBlank String studentId,
        @NotNull @DecimalMin("0") @DecimalMax("100") Double attendancePercentage
    ) {}
    public record AttendanceSaveRequest(@NotEmpty @Valid List<AttendanceEntry> entries) {}
    public record AttendanceSaveResult(int saved, List<String> errors) {}

    public AttendanceStatusDto getAttendanceStatus(String facultyEmail, String courseCode, String programme, String academicYear) {
        authorizationService.requireAssignedToCourse(facultyEmail, courseCode, programme, academicYear);

        List<Enrollment> enrollments = enrollmentRepository.findByCourseIdAndProgrammeAndAcademicYear(courseCode, programme, academicYear);
        List<String> studentIds = enrollments.stream().map(Enrollment::getStudentId).distinct().sorted().toList();
        List<Student> students = studentRepository.findAllById(studentIds);
        Map<String, String> studentNames = students.stream().collect(Collectors.toMap(Student::getId, Student::getName));

        Integer majorityBatch = computeMajorityBatch(students);
        boolean legacy = majorityBatch != null && majorityBatch < 23;

        Map<String, Double> attendanceByStudent = attendanceRepository
            .findByCourseIdAndProgrammeAndAcademicYear(courseCode, programme, academicYear).stream()
            .collect(Collectors.toMap(EnrollmentAttendance::getStudentId, EnrollmentAttendance::getAttendancePercentage));

        List<AttendanceRowDto> rows = studentIds.stream()
            .map(id -> new AttendanceRowDto(id, studentNames.getOrDefault(id, id), attendanceByStudent.get(id)))
            .toList();

        return new AttendanceStatusDto(legacy, majorityBatch, rows);
    }

    @Transactional
    public AttendanceSaveResult saveAttendance(
        String facultyEmail, String courseCode, String programme, String academicYear, AttendanceSaveRequest request
    ) {
        authorizationService.requireAssignedToCourse(facultyEmail, courseCode, programme, academicYear);

        int saved = 0;
        List<String> errors = new ArrayList<>();

        for (AttendanceEntry entry : request.entries()) {
            EnrollmentAttendance existing = attendanceRepository
                .findByStudentIdAndCourseIdAndProgrammeAndAcademicYear(entry.studentId(), courseCode, programme, academicYear)
                .orElse(null);

            if (existing != null) {
                existing.setAttendancePercentage(entry.attendancePercentage());
                attendanceRepository.save(existing);
            } else {
                EnrollmentAttendance attendance = new EnrollmentAttendance();
                attendance.setStudentId(entry.studentId());
                attendance.setCourseId(courseCode);
                attendance.setProgramme(programme);
                attendance.setAcademicYear(academicYear);
                attendance.setAttendancePercentage(entry.attendancePercentage());
                attendanceRepository.save(attendance);
            }
            saved++;
        }

        return new AttendanceSaveResult(saved, errors);
    }

    // Mode of the enrolled students' batch numbers; ties go to the lower (earlier) batch.
    private Integer computeMajorityBatch(List<Student> students) {
        if (students.isEmpty()) {
            return null;
        }

        Map<Integer, Long> counts = students.stream()
            .collect(Collectors.groupingBy(Student::getBatch, Collectors.counting()));
        long maxCount = counts.values().stream().mapToLong(Long::longValue).max().orElse(0);

        return counts.entrySet().stream()
            .filter(e -> e.getValue() == maxCount)
            .map(Map.Entry::getKey)
            .min(Integer::compareTo)
            .orElse(null);
    }
}
