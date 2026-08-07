package org.example.copo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.copo.entity.CourseAssignment;
import org.example.copo.service.BulkImportService;
import org.example.copo.service.CourseAssignmentService;
import org.example.copo.service.CourseAssignmentThresholdService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/admin/assignments")
@RequiredArgsConstructor
public class CourseAssignmentController {

    private final CourseAssignmentService assignmentService;
    private final CourseAssignmentThresholdService thresholdService;
    private final BulkImportService bulkImportService;

    public record ThresholdsRequest(double coIndividual, double poIndividual, double coCohort, double poCohort) {}

    private static void validateThreshold(double value, String label) {
        if (value < 0 || value >= 100) {
            throw new IllegalArgumentException(label + " threshold must be between 0 and 99.99.");
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<CourseAssignment>> getAllAssignments() {
        return ResponseEntity.ok(assignmentService.getAllAssignments());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<CourseAssignment> createAssignment(@Valid @RequestBody CourseAssignment assignment) {
        return ResponseEntity.ok(assignmentService.createAssignment(assignment));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{courseCode}/{programme}/{academicYear}/{department}")
    public ResponseEntity<?> deleteAssignment(
            @PathVariable String courseCode,
            @PathVariable String programme,
            @PathVariable String academicYear,
            @PathVariable String department) {
        assignmentService.deleteAssignment(courseCode, programme, academicYear, department);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{courseCode}/{programme}/{academicYear}/{department}/thresholds")
    public ResponseEntity<CourseAssignmentThresholdService.ThresholdsDto> getThresholds(
        @PathVariable String courseCode, @PathVariable String programme,
        @PathVariable String academicYear, @PathVariable String department
    ) {
        return ResponseEntity.ok(thresholdService.getThresholds(courseCode, programme, academicYear, department));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{courseCode}/{programme}/{academicYear}/{department}/thresholds")
    public ResponseEntity<CourseAssignmentThresholdService.ThresholdsDto> updateThresholds(
        @PathVariable String courseCode, @PathVariable String programme,
        @PathVariable String academicYear, @PathVariable String department,
        @RequestBody ThresholdsRequest request
    ) {
        validateThreshold(request.coIndividual(), "CO individual");
        validateThreshold(request.poIndividual(), "PO individual");
        validateThreshold(request.coCohort(), "CO cohort");
        validateThreshold(request.poCohort(), "PO cohort");

        return ResponseEntity.ok(thresholdService.updateThresholds(
            courseCode, programme, academicYear, department,
            new CourseAssignmentThresholdService.ThresholdsDto(
                request.coIndividual(), request.poIndividual(), request.coCohort(), request.poCohort()
            )
        ));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/import")
    public ResponseEntity<BulkImportService.ImportResult> importAssignments(@RequestParam MultipartFile file) throws IOException {
        return ResponseEntity.ok(bulkImportService.importCourseAssignments(file));
    }
}
