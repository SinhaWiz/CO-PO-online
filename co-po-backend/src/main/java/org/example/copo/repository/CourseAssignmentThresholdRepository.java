package org.example.copo.repository;

import org.example.copo.entity.CourseAssignmentThreshold;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CourseAssignmentThresholdRepository extends JpaRepository<CourseAssignmentThreshold, Integer> {
    Optional<CourseAssignmentThreshold> findByCourseCodeAndProgrammeAndAcademicYearAndDepartment(
        String courseCode, String programme, String academicYear, String department
    );
}
