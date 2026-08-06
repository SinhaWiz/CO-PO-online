package org.example.copo.repository;

import org.example.copo.entity.CourseAssessmentSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseAssessmentSectionRepository extends JpaRepository<CourseAssessmentSection, Integer> {
    List<CourseAssessmentSection> findByCourseCodeAndProgrammeOrderBySectionOrderAsc(String courseCode, String programme);
}
