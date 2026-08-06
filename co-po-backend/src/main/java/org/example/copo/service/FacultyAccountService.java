package org.example.copo.service;

import lombok.RequiredArgsConstructor;
import org.example.copo.entity.Faculty;
import org.example.copo.repository.FacultyRepository;
import org.example.copo.security.PasswordMatcher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FacultyAccountService {

    private final FacultyRepository facultyRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordMatcher passwordMatcher;

    @Transactional
    public void changePassword(String currentFacultyEmail, String currentPassword, String newPassword, String confirmPassword) {
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

        Faculty faculty = facultyRepository.findByEmail(currentFacultyEmail)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Faculty session not found."));

        if (!passwordMatcher.matches(currentPassword, faculty.getPassword())) {
            throw badRequest("Current password is incorrect!");
        }

        faculty.setPassword(passwordEncoder.encode(newPassword));
        facultyRepository.save(faculty);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
