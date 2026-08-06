package org.example.copo.controller;

import lombok.RequiredArgsConstructor;
import org.example.copo.service.FacultyAssignmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/faculty/assignments")
@RequiredArgsConstructor
public class FacultyAssignmentController {

    private final FacultyAssignmentService assignmentService;

    @PreAuthorize("hasRole('FACULTY')")
    @GetMapping("/mine")
    public ResponseEntity<List<FacultyAssignmentService.MyAssignmentDto>> getMyAssignments(Authentication authentication) {
        return ResponseEntity.ok(assignmentService.getMyAssignments(authentication.getName()));
    }
}
