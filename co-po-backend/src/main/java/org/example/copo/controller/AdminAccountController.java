package org.example.copo.controller;

import lombok.RequiredArgsConstructor;
import org.example.copo.service.AdminAccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/account")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    public record ChangePasswordRequest(String currentPassword, String newPassword, String confirmPassword) {}
    public record CreateAdminRequest(String email, String password, String confirmPassword) {}
    public record SensitiveActionRequest(String currentPassword) {}
    public record MessageResponse(String message) {}
    public record AdminsResponse(List<AdminAccountService.AdminSummaryDto> admins, long total, long superAdmins, long regularAdmins) {}

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/me")
    public ResponseEntity<AdminAccountService.AdminProfileDto> getCurrentAdmin(Authentication authentication) {
        return ResponseEntity.ok(adminAccountService.getCurrentAdminProfile(authentication.getName()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/change-password")
    public ResponseEntity<MessageResponse> changePassword(
        Authentication authentication,
        @RequestBody ChangePasswordRequest request
    ) {
        adminAccountService.changePassword(
            authentication.getName(),
            request.currentPassword(),
            request.newPassword(),
            request.confirmPassword()
        );

        return ResponseEntity.ok(new MessageResponse("Password changed successfully!"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admins")
    public ResponseEntity<AdminsResponse> getAllAdmins(Authentication authentication) {
        List<AdminAccountService.AdminSummaryDto> admins = adminAccountService.getAllAdmins(authentication.getName());
        long superAdmins = admins.stream().filter(AdminAccountService.AdminSummaryDto::isSuperAdmin).count();
        long regularAdmins = admins.size() - superAdmins;
        return ResponseEntity.ok(new AdminsResponse(admins, admins.size(), superAdmins, regularAdmins));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admins")
    public ResponseEntity<AdminAccountService.AdminSummaryDto> createAdmin(
        Authentication authentication,
        @RequestBody CreateAdminRequest request
    ) {
        return ResponseEntity.ok(
            adminAccountService.createAdmin(
                authentication.getName(),
                request.email(),
                request.password(),
                request.confirmPassword()
            )
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admins/{id}/delete")
    public ResponseEntity<MessageResponse> deleteAdmin(
        Authentication authentication,
        @PathVariable Integer id,
        @RequestBody SensitiveActionRequest request
    ) {
        adminAccountService.deleteAdmin(authentication.getName(), id, request.currentPassword());
        return ResponseEntity.ok(new MessageResponse("Administrator deleted successfully!"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admins/{id}/promote")
    public ResponseEntity<MessageResponse> promoteAdmin(
        Authentication authentication,
        @PathVariable Integer id,
        @RequestBody SensitiveActionRequest request
    ) {
        adminAccountService.promoteToSuperAdmin(authentication.getName(), id, request.currentPassword());
        return ResponseEntity.ok(new MessageResponse("Administrator promoted to Super Admin successfully!"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admins/{id}/demote")
    public ResponseEntity<MessageResponse> demoteAdmin(
        Authentication authentication,
        @PathVariable Integer id,
        @RequestBody SensitiveActionRequest request
    ) {
        adminAccountService.demoteFromSuperAdmin(authentication.getName(), id, request.currentPassword());
        return ResponseEntity.ok(new MessageResponse("Super Admin demoted successfully!"));
    }
}
