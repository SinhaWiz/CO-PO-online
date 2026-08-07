package org.example.copo.controller;

import lombok.RequiredArgsConstructor;
import org.example.copo.service.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY')")
    @GetMapping("/courses/excel")
    public ResponseEntity<byte[]> getCourseExcelReport() throws IOException {
        byte[] data = reportService.generateCourseExcelReport();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=courses_report.xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(data);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY')")
    @GetMapping("/courses/pdf")
    public ResponseEntity<byte[]> getCoursePdfReport() throws IOException {
        byte[] data = reportService.generateCoursePdfReport();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=courses_report.pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(data);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY')")
    @PostMapping("/courses/summarize")
    public ResponseEntity<Map<String, String>> summarizeCourses(@RequestBody Map<String, String> params) {
        String projectId = params.getOrDefault("projectId", "dummy-project");
        String location = params.getOrDefault("location", "us-central1");
        try {
            String summary = reportService.generateCourseSummaryWithAI(projectId, location);
            return ResponseEntity.ok(Map.of("summary", summary));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY')")
    @PostMapping("/feedback/summarize")
    public ResponseEntity<Map<String, String>> summarizeFeedback(@RequestBody Map<String, String> params) {
        String rawFeedback = params.getOrDefault("rawFeedback", "");
        if (rawFeedback.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Paste some feedback text first."));
        }
        String projectId = params.getOrDefault("projectId", "dummy-project");
        String location = params.getOrDefault("location", "us-central1");
        try {
            String summary = reportService.summarizeFeedback(rawFeedback, projectId, location);
            return ResponseEntity.ok(Map.of("summary", summary));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
