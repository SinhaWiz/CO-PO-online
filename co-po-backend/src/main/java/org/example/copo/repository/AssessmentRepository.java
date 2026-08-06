package org.example.copo.repository;

import org.example.copo.entity.Assessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssessmentRepository extends JpaRepository<Assessment, Integer> {
    Optional<Assessment> findBySectionIdAndAcademicYear(Integer sectionId, String academicYear);
}
