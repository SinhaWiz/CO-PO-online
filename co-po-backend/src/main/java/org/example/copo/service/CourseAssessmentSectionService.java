package org.example.copo.service;

import lombok.RequiredArgsConstructor;
import org.example.copo.entity.CourseAssessmentSection;
import org.example.copo.exception.ResourceNotFoundException;
import org.example.copo.repository.CourseAssessmentSectionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Admin-defined assessment sections for a course (e.g. "Quiz 1", "Mid", "Final") -
 * an ordered, freely-named list per course, not a fixed Quiz1-4/Mid/Final set.
 */
@Service
@RequiredArgsConstructor
public class CourseAssessmentSectionService {

    private final CourseAssessmentSectionRepository sectionRepository;

    public List<CourseAssessmentSection> getSections(String courseCode, String programme) {
        return sectionRepository.findByCourseCodeAndProgrammeOrderBySectionOrderAsc(courseCode, programme);
    }

    public CourseAssessmentSection createSection(String courseCode, String programme, String displayName, Integer sectionOrder) {
        CourseAssessmentSection section = new CourseAssessmentSection();
        section.setCourseCode(courseCode);
        section.setProgramme(programme);
        section.setDisplayName(displayName);
        section.setSectionOrder(sectionOrder);
        return sectionRepository.save(section);
    }

    public CourseAssessmentSection updateSection(Integer id, String displayName, Integer sectionOrder) {
        CourseAssessmentSection section = sectionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Assessment section not found: " + id));
        section.setDisplayName(displayName);
        section.setSectionOrder(sectionOrder);
        return sectionRepository.save(section);
    }

    public void deleteSection(Integer id) {
        if (!sectionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Assessment section not found: " + id);
        }
        sectionRepository.deleteById(id);
    }
}
