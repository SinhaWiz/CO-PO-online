package org.example.copo.controller;

import lombok.RequiredArgsConstructor;
import org.example.copo.service.CourseResultService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/faculty/results")
@RequiredArgsConstructor
public class CourseResultController {

    private final CourseResultService resultService;

    @PreAuthorize("hasRole('FACULTY')")
    @GetMapping("/{courseCode}/{programme}/{academicYear}/{department}")
    public ResponseEntity<CourseResultService.ResultsOutcome> getResults(
        Authentication authentication,
        @PathVariable String courseCode, @PathVariable String programme,
        @PathVariable String academicYear, @PathVariable String department
    ) {
        return ResponseEntity.ok(
            resultService.getResults(authentication.getName(), courseCode, programme, academicYear, department)
        );
    }
}
