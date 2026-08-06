package org.example.copo.repository;

import org.example.copo.entity.CourseReportDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CourseReportDraftRepository extends JpaRepository<CourseReportDraft, Integer> {
    Optional<CourseReportDraft> findByCourseCodeAndProgrammeAndAcademicYearAndDepartmentAndFacultyId(
        String courseCode, String programme, String academicYear, String department, String facultyId
    );

    void deleteByCourseCodeAndProgrammeAndAcademicYearAndDepartmentAndFacultyId(
        String courseCode, String programme, String academicYear, String department, String facultyId
    );
}
