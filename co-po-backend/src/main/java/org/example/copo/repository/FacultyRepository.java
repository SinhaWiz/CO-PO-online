package org.example.copo.repository;

import org.example.copo.entity.Faculty;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FacultyRepository extends JpaRepository<Faculty, String> {
    Optional<Faculty> findByEmail(String email);
}