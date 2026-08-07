package org.example.copo.controller;

import lombok.RequiredArgsConstructor;
import org.example.copo.service.AdminReportService;
import org.example.copo.service.FacultyReportService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;

@RestController
@RequestMapping("/api/faculty/reports")
@RequiredArgsConstructor
public class FacultyReportController {

    private final FacultyReportService facultyReportService;
    // Faculty aren't allowed to hit /api/admin/**, so this reuses AdminReportService's
    // already-safe (path-traversal-checked) file resolver behind a faculty-scoped endpoint
    // rather than duplicating that logic here.
    private final AdminReportService adminReportService;

    @PreAuthorize("hasRole('FACULTY')")
    @PostMapping("/co/{courseCode}/{programme}/{academicYear}/{department}")
    public ResponseEntity<FacultyReportService.GeneratedReportResult> generateCoReport(
        Authentication authentication,
        @PathVariable String courseCode, @PathVariable String programme,
        @PathVariable String academicYear, @PathVariable String department,
        @RequestBody(required = false) FacultyReportService.GenerateReportRequest request
    ) {
        return ResponseEntity.ok(facultyReportService.generateCoReport(
            authentication.getName(), courseCode, programme, academicYear, department, request
        ));
    }

    @PreAuthorize("hasRole('FACULTY')")
    @PostMapping("/po/{courseCode}/{programme}/{academicYear}/{department}")
    public ResponseEntity<FacultyReportService.GeneratedReportResult> generatePoReport(
        Authentication authentication,
        @PathVariable String courseCode, @PathVariable String programme,
        @PathVariable String academicYear, @PathVariable String department,
        @RequestBody(required = false) FacultyReportService.GenerateReportRequest request
    ) {
        return ResponseEntity.ok(facultyReportService.generatePoReport(
            authentication.getName(), courseCode, programme, academicYear, department, request
        ));
    }

    @PreAuthorize("hasRole('FACULTY')")
    @GetMapping("/mine")
    public ResponseEntity<List<AdminReportService.ReportFileDto>> listMyReports(Authentication authentication) {
        return ResponseEntity.ok(facultyReportService.listMyReports(authentication.getName()));
    }

    @PreAuthorize("hasRole('FACULTY')")
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadReport(@RequestParam String type, @RequestParam String name) {
        File file = adminReportService.resolveDownloadFile(type, name);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(AdminReportService.contentTypeFor(file)))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
            .body(resource);
    }
}
