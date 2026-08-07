package org.example.copo.controller;

import lombok.RequiredArgsConstructor;
import org.example.copo.service.SummaryReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/faculty/summary-report")
@RequiredArgsConstructor
public class SummaryReportController {

    private final SummaryReportService summaryReportService;

    @PreAuthorize("hasRole('FACULTY')")
    @GetMapping("/{courseCode}/{programme}/{academicYear}/{department}")
    public ResponseEntity<SummaryReportService.SummaryReportPreview> getPreview(
        Authentication authentication,
        @PathVariable String courseCode, @PathVariable String programme,
        @PathVariable String academicYear, @PathVariable String department
    ) {
        return ResponseEntity.ok(
            summaryReportService.getPreview(authentication.getName(), courseCode, programme, academicYear, department)
        );
    }

    @PreAuthorize("hasRole('FACULTY')")
    @PostMapping("/{courseCode}/{programme}/{academicYear}/{department}/generate")
    public ResponseEntity<SummaryReportService.GenerateResult> generate(
        Authentication authentication,
        @PathVariable String courseCode, @PathVariable String programme,
        @PathVariable String academicYear, @PathVariable String department,
        @RequestBody(required = false) SummaryReportService.GenerateSummaryReportRequest request
    ) {
        return ResponseEntity.ok(
            summaryReportService.generateReport(authentication.getName(), courseCode, programme, academicYear, department, request)
        );
    }
}
