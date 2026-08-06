package org.example.copo.repository;

import org.example.copo.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Enrollment.EnrollmentId> {
    List<Enrollment> findByCourseIdAndProgramme(String courseId, String programme);
    List<Enrollment> findByCourseIdAndProgrammeAndAcademicYear(String courseId, String programme, String academicYear);
    List<Enrollment> findByStudentId(String studentId);
}
