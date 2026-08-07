package org.example.copo.controller;

import lombok.RequiredArgsConstructor;
import org.example.copo.service.MarksExcelService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/faculty/marks-excel")
@RequiredArgsConstructor
public class MarksExcelController {

    private final MarksExcelService marksExcelService;

    @PreAuthorize("hasRole('FACULTY')")
    @GetMapping("/{courseCode}/{programme}/{academicYear}/export")
    public ResponseEntity<byte[]> exportMarks(
        Authentication authentication,
        @PathVariable String courseCode, @PathVariable String programme, @PathVariable String academicYear
    ) throws IOException {
        byte[] data = marksExcelService.exportMarks(authentication.getName(), courseCode, programme, academicYear);
        String fileName = "Marks_" + courseCode.replaceAll("[^A-Za-z0-9_-]", "") + "_" + academicYear + ".xlsx";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(data);
    }

    @PreAuthorize("hasRole('FACULTY')")
    @PostMapping("/{courseCode}/{programme}/{academicYear}/import")
    public ResponseEntity<MarksExcelService.MarksImportResult> importMarks(
        Authentication authentication,
        @PathVariable String courseCode, @PathVariable String programme, @PathVariable String academicYear,
        @RequestParam MultipartFile file
    ) throws IOException {
        return ResponseEntity.ok(
            marksExcelService.importMarks(authentication.getName(), courseCode, programme, academicYear, file)
        );
    }
}
