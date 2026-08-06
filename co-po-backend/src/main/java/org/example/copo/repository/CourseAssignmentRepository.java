package org.example.copo.repository;

import org.example.copo.entity.CourseAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseAssignmentRepository extends JpaRepository<CourseAssignment, CourseAssignment.CourseAssignmentId> {
    List<CourseAssignment> findByFacultyId(String facultyId);
    boolean existsByFacultyIdAndCourseCodeAndProgrammeAndAcademicYear(
        String facultyId, String courseCode, String programme, String academicYear
    );
}
