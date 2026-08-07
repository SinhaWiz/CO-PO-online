package org.example.copo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.copo.entity.Enrollment;
import org.example.copo.service.BulkImportService;
import org.example.copo.service.EnrollmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/admin/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;
    private final BulkImportService bulkImportService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<Enrollment>> getAllEnrollments() {
        return ResponseEntity.ok(enrollmentService.getAllEnrollments());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Enrollment> createEnrollment(@Valid @RequestBody Enrollment enrollment) {
        return ResponseEntity.ok(enrollmentService.createEnrollment(enrollment));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{studentId}/{courseId}/{programme}/{academicYear}")
    public ResponseEntity<?> deleteEnrollment(
            @PathVariable String studentId,
            @PathVariable String courseId,
            @PathVariable String programme,
            @PathVariable String academicYear) {
        enrollmentService.deleteEnrollment(studentId, courseId, programme, academicYear);
        return ResponseEntity.ok().build();
    }

    // defaultCourseCode/programme/academicYear mirror the desktop screen's own
    // pre-selected course/year dropdowns - a row's own columns still win when present,
    // these just fill in whatever the row leaves out.
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/import")
    public ResponseEntity<BulkImportService.ImportResult> importEnrollments(
        @RequestParam MultipartFile file,
        @RequestParam(required = false) String defaultCourseCode,
        @RequestParam(required = false) String defaultProgramme,
        @RequestParam(required = false) String defaultAcademicYear
    ) throws IOException {
        return ResponseEntity.ok(bulkImportService.importEnrollments(file, defaultCourseCode, defaultProgramme, defaultAcademicYear));
    }
}
