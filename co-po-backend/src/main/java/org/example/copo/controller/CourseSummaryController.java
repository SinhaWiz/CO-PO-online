package org.example.copo.controller;

import lombok.RequiredArgsConstructor;
import org.example.copo.service.CourseSummaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/faculty/course-summary")
@RequiredArgsConstructor
public class CourseSummaryController {

    private final CourseSummaryService courseSummaryService;

    @PreAuthorize("hasRole('FACULTY')")
    @GetMapping("/{courseCode}/{programme}/{academicYear}")
    public ResponseEntity<CourseSummaryService.CourseSummaryDto> getCourseSummary(
        Authentication authentication,
        @PathVariable String courseCode, @PathVariable String programme, @PathVariable String academicYear
    ) {
        return ResponseEntity.ok(
            courseSummaryService.getCourseSummary(authentication.getName(), courseCode, programme, academicYear)
        );
    }
}
