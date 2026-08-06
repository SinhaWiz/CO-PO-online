package org.example.copo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.copo.service.GradebookService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/faculty/gradebook")
@RequiredArgsConstructor
public class GradebookController {

    private final GradebookService gradebookService;

    @PreAuthorize("hasRole('FACULTY')")
    @GetMapping("/{assessmentId}/roster")
    public ResponseEntity<GradebookService.RosterDto> getRoster(
        Authentication authentication, @PathVariable Integer assessmentId
    ) {
        return ResponseEntity.ok(gradebookService.getRoster(authentication.getName(), assessmentId));
    }

    @PreAuthorize("hasRole('FACULTY')")
    @PostMapping("/{assessmentId}/marks/batch")
    public ResponseEntity<GradebookService.BatchSaveResult> saveBatch(
        Authentication authentication, @PathVariable Integer assessmentId,
        @Valid @RequestBody GradebookService.BatchSaveRequest request
    ) {
        return ResponseEntity.ok(gradebookService.saveBatch(authentication.getName(), assessmentId, request));
    }
}
