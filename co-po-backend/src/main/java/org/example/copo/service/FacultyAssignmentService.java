package org.example.copo.service;

import lombok.RequiredArgsConstructor;
import org.example.copo.entity.Course;
import org.example.copo.entity.CourseAssignment;
import org.example.copo.repository.CourseAssignmentRepository;
import org.example.copo.repository.CourseRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FacultyAssignmentService {

    private final CourseAssignmentRepository courseAssignmentRepository;
    private final CourseRepository courseRepository;
    private final AssignmentAuthorizationService authorizationService;

    public record MyAssignmentDto(String courseCode, String programme, String courseName, String academicYear, String department) {}

    public List<MyAssignmentDto> getMyAssignments(String facultyEmail) {
        String facultyId = authorizationService.resolveFacultyId(facultyEmail);
        List<CourseAssignment> assignments = courseAssignmentRepository.findByFacultyId(facultyId);

        Map<String, String> courseNames = courseRepository.findAll().stream()
            .collect(Collectors.toMap(
                c -> c.getCourseCode() + "||" + c.getProgramme(),
                Course::getCourseName,
                (a, b) -> a
            ));

        return assignments.stream()
            .map(a -> new MyAssignmentDto(
                a.getCourseCode(),
                a.getProgramme(),
                courseNames.getOrDefault(a.getCourseCode() + "||" + a.getProgramme(), a.getCourseCode()),
                a.getAcademicYear(),
                a.getDepartment()
            ))
            .sorted(Comparator.comparing(MyAssignmentDto::academicYear).reversed().thenComparing(MyAssignmentDto::courseCode))
            .toList();
    }
}
