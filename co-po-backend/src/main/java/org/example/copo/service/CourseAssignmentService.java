package org.example.copo.service;

import lombok.RequiredArgsConstructor;
import org.example.copo.entity.CourseAssignment;
import org.example.copo.exception.ResourceNotFoundException;
import org.example.copo.repository.CourseAssignmentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseAssignmentService {

    private final CourseAssignmentRepository courseAssignmentRepository;

    public List<CourseAssignment> getAllAssignments() {
        return courseAssignmentRepository.findAll();
    }

    public List<CourseAssignment> getAssignmentsByFaculty(String facultyId) {
        return courseAssignmentRepository.findByFacultyId(facultyId);
    }

    public CourseAssignment createAssignment(CourseAssignment assignment) {
        return courseAssignmentRepository.save(assignment);
    }

    public void deleteAssignment(String courseCode, String programme, String academicYear, String department) {
        CourseAssignment.CourseAssignmentId id = new CourseAssignment.CourseAssignmentId(courseCode, programme, academicYear, department);
        if (!courseAssignmentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Course assignment not found: " + courseCode + "/" + programme + "/" + academicYear + "/" + department);
        }
        courseAssignmentRepository.deleteById(id);
    }
}
