package org.example.copo.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Checks a raw password against a stored one, whether that's a bcrypt hash or a
 * legacy plaintext value (rows created before password hashing was in place).
 * Shared so every login/password-check path (Admin, Faculty) applies the same rule.
 */
@Component
@RequiredArgsConstructor
public class PasswordMatcher {

    private final PasswordEncoder passwordEncoder;

    public boolean matches(String rawPassword, String storedPassword) {
        if (storedPassword == null) {
            return false;
        }

        if (storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$") || storedPassword.startsWith("$2y$")) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }

        return rawPassword.equals(storedPassword);
    }
}
