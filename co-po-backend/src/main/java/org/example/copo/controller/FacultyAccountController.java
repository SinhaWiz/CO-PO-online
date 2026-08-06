package org.example.copo.controller;

import lombok.RequiredArgsConstructor;
import org.example.copo.service.FacultyAccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/faculty/account")
@RequiredArgsConstructor
public class FacultyAccountController {

    private final FacultyAccountService facultyAccountService;

    public record ChangePasswordRequest(String currentPassword, String newPassword, String confirmPassword) {}
    public record MessageResponse(String message) {}

    @PreAuthorize("hasRole('FACULTY')")
    @PostMapping("/change-password")
    public ResponseEntity<MessageResponse> changePassword(
        Authentication authentication,
        @RequestBody ChangePasswordRequest request
    ) {
        facultyAccountService.changePassword(
            authentication.getName(),
            request.currentPassword(),
            request.newPassword(),
            request.confirmPassword()
        );

        return ResponseEntity.ok(new MessageResponse("Password changed successfully!"));
    }
}
