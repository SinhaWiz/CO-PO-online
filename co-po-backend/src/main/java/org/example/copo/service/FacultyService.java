package org.example.copo.service;

import lombok.RequiredArgsConstructor;
import org.example.copo.entity.Faculty;
import org.example.copo.repository.FacultyRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FacultyService {

    private final FacultyRepository facultyRepository;
    private final PasswordEncoder passwordEncoder;

    public List<Faculty> getAllFaculties() {
        return facultyRepository.findAll();
    }

    public Faculty createFaculty(Faculty faculty) {
        faculty.setPassword(passwordEncoder.encode(faculty.getPassword()));
        return facultyRepository.save(faculty);
    }

    public Faculty updateFaculty(String id, Faculty facultyDetails) {
        Faculty faculty = facultyRepository.findById(id).orElseThrow(() -> new RuntimeException("Faculty not found"));
        faculty.setShortname(facultyDetails.getShortname());
        faculty.setFullName(facultyDetails.getFullName());
        faculty.setEmail(facultyDetails.getEmail());
        if (facultyDetails.getPassword() != null && !facultyDetails.getPassword().isEmpty()) {
            faculty.setPassword(passwordEncoder.encode(facultyDetails.getPassword()));
        }
        return facultyRepository.save(faculty);
    }

    public void deleteFaculty(String id) {
        facultyRepository.deleteById(id);
    }
}
