package org.example.copo.controller;

import lombok.RequiredArgsConstructor;
import org.example.copo.service.AttainmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/faculty/attainment")
@RequiredArgsConstructor
public class AttainmentController {

    private final AttainmentService attainmentService;

    @PreAuthorize("hasRole('FACULTY')")
    @GetMapping("/co/{courseCode}/{programme}/{academicYear}/{department}")
    public ResponseEntity<AttainmentService.AttainmentResult> getCoAttainment(
        Authentication authentication,
        @PathVariable String courseCode, @PathVariable String programme,
        @PathVariable String academicYear, @PathVariable String department
    ) {
        return ResponseEntity.ok(
            attainmentService.getCoAttainment(authentication.getName(), courseCode, programme, academicYear, department)
        );
    }
}
