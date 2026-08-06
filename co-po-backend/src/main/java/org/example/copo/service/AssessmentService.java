package org.example.copo.service;

import lombok.RequiredArgsConstructor;
import org.example.copo.entity.Assessment;
import org.example.copo.repository.AssessmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assessment = one academic year's "instance" of a CourseAssessmentSection. There's no
 * separate admin/faculty creation step for these in the desktop app either - they come
 * into existence the first time a faculty member works on a section for a given year.
 * getOrCreate mirrors that: safe to call every time a faculty member opens a section,
 * whether or not this is the first time for that year.
 */
@Service
@RequiredArgsConstructor
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;

    @Transactional
    public Assessment getOrCreate(Integer sectionId, String academicYear) {
        return assessmentRepository.findBySectionIdAndAcademicYear(sectionId, academicYear)
            .orElseGet(() -> {
                Assessment assessment = new Assessment();
                assessment.setSectionId(sectionId);
                assessment.setAcademicYear(academicYear);
                assessment.setTotalMarks(0.0);
                return assessmentRepository.save(assessment);
            });
    }
}
