package org.example.copo.controller;

import org.example.copo.service.AdminReportService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;

@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private final AdminReportService adminReportService;

    public AdminReportController(AdminReportService adminReportService) {
        this.adminReportService = adminReportService;
    }

    public record CohortGenerateRequest(String programme, Integer batch) {}

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/cohort/programmes")
    public ResponseEntity<List<String>> getCohortProgrammes() {
        return ResponseEntity.ok(adminReportService.getDistinctProgrammesFromStudents());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/cohort/batches")
    public ResponseEntity<List<Integer>> getCohortBatches(@RequestParam String programme) {
        return ResponseEntity.ok(adminReportService.getBatchesForProgramme(programme));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/cohort/students")
    public ResponseEntity<List<AdminReportService.CohortStudentDto>> getCohortStudents(
        @RequestParam String programme,
        @RequestParam int batch
    ) {
        return ResponseEntity.ok(adminReportService.getGraduatingCohort(programme, batch));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/cohort/generate")
    public ResponseEntity<AdminReportService.CohortPoReportResultDto> generateCohortReport(
        @RequestBody CohortGenerateRequest request
    ) {
        if (request == null || request.programme() == null || request.programme().isBlank() || request.batch() == null) {
            return ResponseEntity.badRequest().body(new AdminReportService.CohortPoReportResultDto(
                List.of(),
                List.of("Programme and batch are required."),
                null
            ));
        }
        return ResponseEntity.ok(adminReportService.generateCohortPoReport(request.programme(), request.batch()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/files")
    public ResponseEntity<List<AdminReportService.ReportFileDto>> listReports() {
        return ResponseEntity.ok(adminReportService.listReportFiles());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/files/download")
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
