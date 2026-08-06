package org.example.copo.controller;

import org.example.copo.dto.JwtAuthResponse;
import org.example.copo.dto.LoginRequest;
import org.example.copo.entity.Admin;
import org.example.copo.entity.Faculty;
import org.example.copo.repository.AdminRepository;
import org.example.copo.repository.FacultyRepository;
import org.example.copo.security.JwtTokenProvider;
import org.example.copo.security.PasswordMatcher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AdminRepository adminRepository;
    private final FacultyRepository facultyRepository;
    private final PasswordMatcher passwordMatcher;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        String email = loginRequest.getEmail();
        String rawPassword = loginRequest.getPassword();
        String role = loginRequest.getRole().toUpperCase();

        if ("ADMIN".equals(role)) {
            Optional<Admin> adminOpt = adminRepository.findByEmail(email);
            if (adminOpt.isPresent()) {
                Admin admin = adminOpt.get();
                if (passwordMatcher.matches(rawPassword, admin.getPassword())) {
                    String token = jwtTokenProvider.generateToken(email, "ROLE_ADMIN", "Admin");
                    return ResponseEntity.ok(new JwtAuthResponse(token, email, "ROLE_ADMIN", "Admin"));
                }
            }
        } else if ("FACULTY".equals(role)) {
            Optional<Faculty> facultyOpt = facultyRepository.findByEmail(email);
            if (facultyOpt.isPresent()) {
                Faculty faculty = facultyOpt.get();
                if (passwordMatcher.matches(rawPassword, faculty.getPassword())) {
                    String token = jwtTokenProvider.generateToken(email, "ROLE_FACULTY", faculty.getFullName());
                    return ResponseEntity.ok(new JwtAuthResponse(token, email, "ROLE_FACULTY", faculty.getFullName()));
                }
            }
        } else {
            return ResponseEntity.badRequest().body("Invalid role specified.");
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password");
    }
}
