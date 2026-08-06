package org.example.copo.service;

import lombok.RequiredArgsConstructor;
import org.example.copo.entity.Admin;
import org.example.copo.repository.AdminRepository;
import org.example.copo.security.PasswordMatcher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordMatcher passwordMatcher;
    private final JdbcTemplate jdbcTemplate;

    public record AdminProfileDto(
        Integer id,
        String email,
        boolean isSuperAdmin,
        String createdBy,
        LocalDateTime createdAt
    ) {}

    public record AdminSummaryDto(
        Integer id,
        String email,
        boolean isSuperAdmin,
        String createdBy,
        LocalDateTime createdAt
    ) {}

    @Transactional(readOnly = true)
    public AdminProfileDto getCurrentAdminProfile(String currentAdminEmail) {
        ensureAdminColumns();
        Admin admin = getAdminByEmailOrThrow(currentAdminEmail);
        return new AdminProfileDto(
            admin.getId(),
            admin.getEmail(),
            Boolean.TRUE.equals(admin.getIsSuperAdmin()),
            admin.getCreatedBy(),
            admin.getCreatedAt()
        );
    }

    @Transactional
    public void changePassword(String currentAdminEmail, String currentPassword, String newPassword, String confirmPassword) {
        ensureAdminColumns();

        if (isBlank(currentPassword) || isBlank(newPassword) || isBlank(confirmPassword)) {
            throw badRequest("All fields are required!");
        }

        if (newPassword.length() < 6) {
            throw badRequest("New password must be at least 6 characters long!");
        }

        if (!newPassword.equals(confirmPassword)) {
            throw badRequest("New passwords do not match!");
        }

        if (currentPassword.equals(newPassword)) {
            throw badRequest("New password must be different from current password!");
        }

        Admin currentAdmin = getAdminByEmailOrThrow(currentAdminEmail);
        if (!passwordMatcher.matches(currentPassword, currentAdmin.getPassword())) {
            throw badRequest("Current password is incorrect!");
        }

        currentAdmin.setPassword(passwordEncoder.encode(newPassword));
        adminRepository.save(currentAdmin);
    }

    @Transactional(readOnly = true)
    public List<AdminSummaryDto> getAllAdmins(String currentAdminEmail) {
        ensureAdminColumns();
        requireSuperAdmin(currentAdminEmail);

        return adminRepository.findAll().stream()
            .sorted(Comparator.comparing(Admin::getId))
            .map(admin -> new AdminSummaryDto(
                admin.getId(),
                admin.getEmail(),
                Boolean.TRUE.equals(admin.getIsSuperAdmin()),
                admin.getCreatedBy(),
                admin.getCreatedAt()
            ))
            .toList();
    }

    @Transactional
    public AdminSummaryDto createAdmin(String currentAdminEmail, String email, String password, String confirmPassword) {
        ensureAdminColumns();
        requireSuperAdmin(currentAdminEmail);

        if (isBlank(email) || isBlank(password) || isBlank(confirmPassword)) {
            throw badRequest("All fields are required!");
        }

        String normalizedEmail = email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw badRequest("Invalid email format!");
        }

        if (password.length() < 6) {
            throw badRequest("Password must be at least 6 characters long!");
        }

        if (!password.equals(confirmPassword)) {
            throw badRequest("Passwords do not match!");
        }

        if (adminRepository.existsByEmail(normalizedEmail)) {
            throw badRequest("An administrator with this email already exists!");
        }

        Admin admin = new Admin();
        admin.setEmail(normalizedEmail);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setIsSuperAdmin(false);
        admin.setCreatedBy(currentAdminEmail);

        Admin saved = adminRepository.save(admin);
        return new AdminSummaryDto(
            saved.getId(),
            saved.getEmail(),
            Boolean.TRUE.equals(saved.getIsSuperAdmin()),
            saved.getCreatedBy(),
            saved.getCreatedAt()
        );
    }

    @Transactional
    public void deleteAdmin(String currentAdminEmail, Integer targetAdminId, String currentPassword) {
        ensureAdminColumns();
        validateSensitiveActionAndSuperAdmin(currentAdminEmail, currentPassword);

        Admin currentAdmin = getAdminByEmailOrThrow(currentAdminEmail);
        Admin target = adminRepository.findById(targetAdminId)
            .orElseThrow(() -> notFound("Administrator not found."));

        if (currentAdmin.getId().equals(target.getId())) {
            throw badRequest("You cannot delete your own account!");
        }

        if (Boolean.TRUE.equals(target.getIsSuperAdmin()) && adminRepository.countByIsSuperAdminTrue() <= 1) {
            throw badRequest("Cannot delete the last super admin!");
        }

        adminRepository.delete(target);
    }

    @Transactional
    public void promoteToSuperAdmin(String currentAdminEmail, Integer targetAdminId, String currentPassword) {
        ensureAdminColumns();
        validateSensitiveActionAndSuperAdmin(currentAdminEmail, currentPassword);

        Admin target = adminRepository.findById(targetAdminId)
            .orElseThrow(() -> notFound("Administrator not found."));

        if (Boolean.TRUE.equals(target.getIsSuperAdmin())) {
            throw badRequest("Administrator is already a super admin.");
        }

        target.setIsSuperAdmin(true);
        adminRepository.save(target);
    }

    @Transactional
    public void demoteFromSuperAdmin(String currentAdminEmail, Integer targetAdminId, String currentPassword) {
        ensureAdminColumns();
        validateSensitiveActionAndSuperAdmin(currentAdminEmail, currentPassword);

        Admin currentAdmin = getAdminByEmailOrThrow(currentAdminEmail);
        Admin target = adminRepository.findById(targetAdminId)
            .orElseThrow(() -> notFound("Administrator not found."));

        if (!Boolean.TRUE.equals(target.getIsSuperAdmin())) {
            throw badRequest("Administrator is not a super admin.");
        }

        if (currentAdmin.getId().equals(target.getId())) {
            throw badRequest("You cannot demote your own account!");
        }

        if (adminRepository.countByIsSuperAdminTrue() <= 1) {
            throw badRequest("Cannot demote the last super admin!");
        }

        target.setIsSuperAdmin(false);
        adminRepository.save(target);
    }

    private void validateSensitiveActionAndSuperAdmin(String currentAdminEmail, String enteredPassword) {
        requireSuperAdmin(currentAdminEmail);
        if (isBlank(enteredPassword)) {
            throw badRequest("Password is required for confirmation.");
        }

        Admin currentAdmin = getAdminByEmailOrThrow(currentAdminEmail);
        if (!passwordMatcher.matches(enteredPassword, currentAdmin.getPassword())) {
            throw badRequest("Incorrect password. Operation cancelled.");
        }
    }

    private void requireSuperAdmin(String currentAdminEmail) {
        Admin admin = getAdminByEmailOrThrow(currentAdminEmail);
        if (!Boolean.TRUE.equals(admin.getIsSuperAdmin())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only super admins can perform this action.");
        }
    }

    private Admin getAdminByEmailOrThrow(String email) {
        return adminRepository.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin session not found."));
    }

    private void ensureAdminColumns() {
        jdbcTemplate.execute("ALTER TABLE Admin ADD COLUMN IF NOT EXISTS is_super_admin BOOLEAN DEFAULT FALSE");
        jdbcTemplate.execute("ALTER TABLE Admin ADD COLUMN IF NOT EXISTS created_by VARCHAR(100)");
        jdbcTemplate.execute("ALTER TABLE Admin ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");

        jdbcTemplate.update("UPDATE Admin SET is_super_admin = FALSE WHERE is_super_admin IS NULL");
        jdbcTemplate.update("UPDATE Admin SET created_by = 'system' WHERE created_by IS NULL");
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
