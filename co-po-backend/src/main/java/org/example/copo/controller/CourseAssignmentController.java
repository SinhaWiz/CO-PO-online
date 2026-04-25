package org.example.copo.controller;

import lombok.RequiredArgsConstructor;
import org.example.copo.entity.CourseAssignment;
import org.example.copo.service.CourseAssignmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/assignments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class CourseAssignmentController {

    private final CourseAssignmentService assignmentService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<CourseAssignment>> getAllAssignments() {
        return ResponseEntity.ok(assignmentService.getAllAssignments());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<CourseAssignment> createAssignment(@RequestBody CourseAssignment assignment) {
        return ResponseEntity.ok(assignmentService.createAssignment(assignment));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{courseCode}/{programme}/{academicYear}/{department}")
    public ResponseEntity<?> deleteAssignment(
            @PathVariable String courseCode,
            @PathVariable String programme,
            @PathVariable String academicYear,
            @PathVariable String department) {
        assignmentService.deleteAssignment(courseCode, programme, academicYear, department);
        return ResponseEntity.ok().build();
    }
}
