package org.example.copo.controller;

import lombok.RequiredArgsConstructor;
import org.example.copo.service.MarksReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/faculty/marks-report")
@RequiredArgsConstructor
public class MarksReportController {

    private final MarksReportService marksReportService;

    public record DetailedMarksRequest(List<Integer> sectionIds, boolean includeOverallCoAttainment) {}
    public record ConsolidatedMarksRequest(List<Integer> sectionIds) {}

    @PreAuthorize("hasRole('FACULTY')")
    @PostMapping("/{courseCode}/{programme}/{academicYear}/{department}/detailed")
    public ResponseEntity<MarksReportService.GenerateResult> generateDetailed(
        Authentication authentication,
        @PathVariable String courseCode, @PathVariable String programme,
        @PathVariable String academicYear, @PathVariable String department,
        @RequestBody DetailedMarksRequest request
    ) {
        return ResponseEntity.ok(marksReportService.generateDetailedMarksReport(
            authentication.getName(), courseCode, programme, academicYear, department,
            request.sectionIds(), request.includeOverallCoAttainment()
        ));
    }

    @PreAuthorize("hasRole('FACULTY')")
    @PostMapping("/{courseCode}/{programme}/{academicYear}/{department}/consolidated")
    public ResponseEntity<MarksReportService.GenerateResult> generateConsolidated(
        Authentication authentication,
        @PathVariable String courseCode, @PathVariable String programme,
        @PathVariable String academicYear, @PathVariable String department,
        @RequestBody ConsolidatedMarksRequest request
    ) {
        return ResponseEntity.ok(marksReportService.generateConsolidatedMarksReport(
            authentication.getName(), courseCode, programme, academicYear, department,
            request.sectionIds()
        ));
    }
}
