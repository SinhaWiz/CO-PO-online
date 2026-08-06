package org.example.copo.service;

import lombok.RequiredArgsConstructor;
import org.example.copo.entity.Assessment;
import org.example.copo.entity.CourseAssessmentSection;
import org.example.copo.exception.ResourceNotFoundException;
import org.example.copo.repository.AssessmentRepository;
import org.example.copo.repository.CourseAssessmentSectionRepository;
import org.example.copo.repository.CourseAssignmentRepository;
import org.example.copo.repository.FacultyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * "Is the logged-in faculty member actually assigned to this course offering?" Nothing
 * in the assessment/marks API checked this before now - any authenticated FACULTY user
 * could read or write any other faculty member's questions and marks by guessing ids.
 * Shared here since both question management (Phase 2) and marks entry (Phase 3) need
 * the exact same check.
 */
@Service
@RequiredArgsConstructor
public class AssignmentAuthorizationService {

    private final FacultyRepository facultyRepository;
    private final CourseAssignmentRepository courseAssignmentRepository;
    private final AssessmentRepository assessmentRepository;
    private final CourseAssessmentSectionRepository sectionRepository;

    public String resolveFacultyId(String facultyEmail) {
        return facultyRepository.findByEmail(facultyEmail)
            .map(faculty -> faculty.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Faculty session not found."));
    }

    public void requireOwnsSection(String facultyEmail, Integer sectionId, String academicYear) {
        CourseAssessmentSection section = sectionRepository.findById(sectionId)
            .orElseThrow(() -> new ResourceNotFoundException("Assessment section not found: " + sectionId));
        requireAssignedToCourse(facultyEmail, section.getCourseCode(), section.getProgramme(), academicYear);
    }

    public void requireOwnsAssessment(String facultyEmail, Integer assessmentId) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Assessment not found: " + assessmentId));
        requireOwnsSection(facultyEmail, assessment.getSectionId(), assessment.getAcademicYear());
    }

    // Public entry point for things scoped directly to a course offering rather than a
    // section/assessment - attendance, for one, isn't a question-bearing section at all.
    public void requireAssignedToCourse(String facultyEmail, String courseCode, String programme, String academicYear) {
        String facultyId = resolveFacultyId(facultyEmail);
        boolean assigned = courseAssignmentRepository.existsByFacultyIdAndCourseCodeAndProgrammeAndAcademicYear(
            facultyId, courseCode, programme, academicYear
        );
        if (!assigned) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You are not assigned to " + courseCode + " (" + programme + ") for " + academicYear + "."
            );
        }
    }
}
