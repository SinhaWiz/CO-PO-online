package org.example.copo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.copo.entity.Enrollment;
import org.example.copo.service.EnrollmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

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
}
