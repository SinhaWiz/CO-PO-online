package org.example.copo.controller;

import org.example.copo.dto.JwtAuthResponse;
import org.example.copo.dto.LoginRequest;
import org.example.copo.entity.Admin;
import org.example.copo.entity.Faculty;
import org.example.copo.repository.AdminRepository;
import org.example.copo.repository.FacultyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Optional;
// Basic dummy setup. A real JWT utility class will be integrated next.
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private FacultyRepository facultyRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        String email = loginRequest.getEmail();
        String rawPassword = loginRequest.getPassword();
        String role = loginRequest.getRole().toUpperCase();

        if ("ADMIN".equals(role)) {
            Optional<Admin> adminOpt = adminRepository.findByEmail(email);
            if (adminOpt.isPresent()) {
                Admin admin = adminOpt.get();
                if (passwordEncoder.matches(rawPassword, admin.getPassword()) || rawPassword.equals(admin.getPassword())) {
                    // For legacy fallback if pass isn't hashed yet
                    String dummyToken = "jwt-dummy-token-for-" + email;
                    return ResponseEntity.ok(new JwtAuthResponse(dummyToken, email, "ROLE_ADMIN", "Admin"));
                }
            }
        } else if ("FACULTY".equals(role)) {
            Optional<Faculty> facultyOpt = facultyRepository.findByEmail(email);
            if (facultyOpt.isPresent()) {
                Faculty faculty = facultyOpt.get();
                if (passwordEncoder.matches(rawPassword, faculty.getPassword()) || rawPassword.equals(faculty.getPassword())) {
                    String dummyToken = "jwt-dummy-token-for-" + email;
                    return ResponseEntity.ok(new JwtAuthResponse(dummyToken, email, "ROLE_FACULTY", faculty.getFullName()));
                }
            }
        } else {
            return ResponseEntity.badRequest().body("Invalid role specified.");
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password");
    }
}