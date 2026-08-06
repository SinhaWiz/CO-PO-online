package org.example.copo.service;

import lombok.RequiredArgsConstructor;
import org.example.copo.entity.Course;
import org.example.copo.exception.ResourceNotFoundException;
import org.example.copo.repository.CourseRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;

    public List<Course> getAllCourses() {
        return courseRepository.findAll();
    }

    public Course createCourse(Course course) {
        return courseRepository.save(course);
    }

    public Course updateCourse(String courseCode, String programme, Course courseDetails) {
        Course.CourseId id = new Course.CourseId(courseCode, programme);
        Course course = courseRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + courseCode + "/" + programme));
        course.setCourseName(courseDetails.getCourseName());
        course.setCredits(courseDetails.getCredits());
        course.setDepartment(courseDetails.getDepartment());
        return courseRepository.save(course);
    }

    public void deleteCourse(String courseCode, String programme) {
        Course.CourseId id = new Course.CourseId(courseCode, programme);
        if (!courseRepository.existsById(id)) {
            throw new ResourceNotFoundException("Course not found: " + courseCode + "/" + programme);
        }
        courseRepository.deleteById(id);
    }
}
