package org.example.copo.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.example.copo.entity.Course;
import org.example.copo.entity.CourseAssessmentSection;
import org.example.copo.service.BulkImportService;
import org.example.copo.service.CourseAssessmentSectionService;
import org.example.copo.service.CourseOutcomeService;
import org.example.copo.service.CourseService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/admin/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final CourseOutcomeService courseOutcomeService;
    private final CourseAssessmentSectionService sectionService;
    private final BulkImportService bulkImportService;

    public record CourseOutcomesRequest(List<Integer> coIds, List<Integer> poIds) {}
    public record SectionRequest(@NotBlank String displayName, @NotNull Integer sectionOrder) {}

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

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{courseCode}/{programme}/sections")
    public ResponseEntity<List<CourseAssessmentSection>> getSections(
        @PathVariable String courseCode, @PathVariable String programme
    ) {
        return ResponseEntity.ok(sectionService.getSections(courseCode, programme));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{courseCode}/{programme}/sections")
    public ResponseEntity<CourseAssessmentSection> createSection(
        @PathVariable String courseCode, @PathVariable String programme, @Valid @RequestBody SectionRequest request
    ) {
        return ResponseEntity.ok(sectionService.createSection(courseCode, programme, request.displayName(), request.sectionOrder()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{courseCode}/{programme}/sections/{id}")
    public ResponseEntity<CourseAssessmentSection> updateSection(
        @PathVariable String courseCode, @PathVariable String programme, @PathVariable Integer id,
        @Valid @RequestBody SectionRequest request
    ) {
        return ResponseEntity.ok(sectionService.updateSection(id, request.displayName(), request.sectionOrder()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{courseCode}/{programme}/sections/{id}")
    public ResponseEntity<?> deleteSection(
        @PathVariable String courseCode, @PathVariable String programme, @PathVariable Integer id
    ) {
        sectionService.deleteSection(id);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/import")
    public ResponseEntity<BulkImportService.ImportResult> importCourses(@RequestParam MultipartFile file) throws IOException {
        return ResponseEntity.ok(bulkImportService.importCourses(file));
    }
}
