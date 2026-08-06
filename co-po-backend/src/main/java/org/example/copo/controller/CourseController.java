package org.example.copo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.copo.entity.Course;
import org.example.copo.service.CourseOutcomeService;
import org.example.copo.service.CourseService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final CourseOutcomeService courseOutcomeService;

    public record CourseOutcomesRequest(List<Integer> coIds, List<Integer> poIds) {}

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<Course>> getAllCourses() {
        return ResponseEntity.ok(courseService.getAllCourses());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Course> createCourse(@Valid @RequestBody Course course) {
        return ResponseEntity.ok(courseService.createCourse(course));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{courseCode}/{programme}")
    public ResponseEntity<Course> updateCourse(@PathVariable String courseCode, @PathVariable String programme, @Valid @RequestBody Course course) {
        return ResponseEntity.ok(courseService.updateCourse(courseCode, programme, course));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{courseCode}/{programme}")
    public ResponseEntity<?> deleteCourse(@PathVariable String courseCode, @PathVariable String programme) {
        courseService.deleteCourse(courseCode, programme);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{courseCode}/{programme}/outcomes")
    public ResponseEntity<CourseOutcomeService.CourseOutcomesDto> getCourseOutcomes(
        @PathVariable String courseCode, @PathVariable String programme
    ) {
        return ResponseEntity.ok(courseOutcomeService.getCourseOutcomes(courseCode, programme));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{courseCode}/{programme}/outcomes")
    public ResponseEntity<CourseOutcomeService.CourseOutcomesDto> updateCourseOutcomes(
        @PathVariable String courseCode, @PathVariable String programme,
        @RequestBody CourseOutcomesRequest request
    ) {
        return ResponseEntity.ok(
            courseOutcomeService.updateCourseOutcomes(courseCode, programme, request.coIds(), request.poIds())
        );
    }
}
