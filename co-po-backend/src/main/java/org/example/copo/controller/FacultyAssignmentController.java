package org.example.copo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.copo.service.AssignmentAuthorizationService;
import org.example.copo.service.CourseAssignmentThresholdService;
import org.example.copo.service.FacultyAssignmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/faculty/assignments")
@RequiredArgsConstructor
public class FacultyAssignmentController {

    private final FacultyAssignmentService assignmentService;
    private final CourseAssignmentThresholdService thresholdService;
    private final AssignmentAuthorizationService authorizationService;

    public record ThresholdsRequest(double coIndividual, double poIndividual, double coCohort, double poCohort) {}

    private static void validateThreshold(double value, String label) {
        if (value < 0 || value >= 100) {
            throw new IllegalArgumentException(label + " threshold must be between 0 and 99.99.");
        }
    }

    @PreAuthorize("hasRole('FACULTY')")
    @GetMapping("/mine")
    public ResponseEntity<List<FacultyAssignmentService.MyAssignmentDto>> getMyAssignments(Authentication authentication) {
        return ResponseEntity.ok(assignmentService.getMyAssignments(authentication.getName()));
    }

    @PreAuthorize("hasRole('FACULTY')")
    @GetMapping("/{courseCode}/{programme}/{academicYear}/{department}/thresholds")
    public ResponseEntity<CourseAssignmentThresholdService.ThresholdsDto> getThresholds(
        Authentication authentication,
        @PathVariable String courseCode, @PathVariable String programme,
        @PathVariable String academicYear, @PathVariable String department
    ) {
        authorizationService.requireAssignedToCourse(authentication.getName(), courseCode, programme, academicYear);
        return ResponseEntity.ok(thresholdService.getThresholds(courseCode, programme, academicYear, department));
    }

    @PreAuthorize("hasRole('FACULTY')")
    @PutMapping("/{courseCode}/{programme}/{academicYear}/{department}/thresholds")
    public ResponseEntity<CourseAssignmentThresholdService.ThresholdsDto> updateThresholds(
        Authentication authentication,
        @PathVariable String courseCode, @PathVariable String programme,
        @PathVariable String academicYear, @PathVariable String department,
        @Valid @RequestBody ThresholdsRequest request
    ) {
        authorizationService.requireAssignedToCourse(authentication.getName(), courseCode, programme, academicYear);
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
}
