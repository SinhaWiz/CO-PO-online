package org.example.copo.controller;

import lombok.RequiredArgsConstructor;
import org.example.copo.service.CourseReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/faculty/course-report")
@RequiredArgsConstructor
public class CourseReportController {

    private final CourseReportService courseReportService;

    public record SaveDraftResponse(String updatedAt) {}

    @PreAuthorize("hasRole('FACULTY')")
    @GetMapping("/{courseCode}/{programme}/{academicYear}/{department}")
    public ResponseEntity<CourseReportService.CourseReportContext> getContext(
        Authentication authentication,
        @PathVariable String courseCode, @PathVariable String programme,
        @PathVariable String academicYear, @PathVariable String department
    ) {
        return ResponseEntity.ok(
            courseReportService.getContext(authentication.getName(), courseCode, programme, academicYear, department)
        );
    }

    @PreAuthorize("hasRole('FACULTY')")
    @PutMapping("/{courseCode}/{programme}/{academicYear}/{department}")
    public ResponseEntity<SaveDraftResponse> saveDraft(
        Authentication authentication,
        @PathVariable String courseCode, @PathVariable String programme,
        @PathVariable String academicYear, @PathVariable String department,
        @RequestBody CourseReportService.CourseReportForm form
    ) {
        String updatedAt = courseReportService.saveDraft(
            authentication.getName(), courseCode, programme, academicYear, department, form
        );
        return ResponseEntity.ok(new SaveDraftResponse(updatedAt));
    }

    @PreAuthorize("hasRole('FACULTY')")
    @DeleteMapping("/{courseCode}/{programme}/{academicYear}/{department}")
    public ResponseEntity<Void> clearDraft(
        Authentication authentication,
        @PathVariable String courseCode, @PathVariable String programme,
        @PathVariable String academicYear, @PathVariable String department
    ) {
        courseReportService.clearDraft(authentication.getName(), courseCode, programme, academicYear, department);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('FACULTY')")
    @PostMapping("/{courseCode}/{programme}/{academicYear}/{department}/generate")
    public ResponseEntity<CourseReportService.GenerateResult> generate(
        Authentication authentication,
        @PathVariable String courseCode, @PathVariable String programme,
        @PathVariable String academicYear, @PathVariable String department,
        @RequestBody CourseReportService.CourseReportForm form
    ) {
        return ResponseEntity.ok(
            courseReportService.generateReport(authentication.getName(), courseCode, programme, academicYear, department, form)
        );
    }
}
