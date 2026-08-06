package org.example.copo.repository;

import org.example.copo.entity.EnrollmentAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnrollmentAttendanceRepository extends JpaRepository<EnrollmentAttendance, EnrollmentAttendance.EnrollmentAttendanceId> {
    List<EnrollmentAttendance> findByCourseIdAndProgrammeAndAcademicYear(String courseId, String programme, String academicYear);
    Optional<EnrollmentAttendance> findByStudentIdAndCourseIdAndProgrammeAndAcademicYear(
        String studentId, String courseId, String programme, String academicYear
    );
}
