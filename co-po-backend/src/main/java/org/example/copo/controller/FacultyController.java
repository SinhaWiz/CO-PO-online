package org.example.copo.controller;

import lombok.RequiredArgsConstructor;
import org.example.copo.entity.Faculty;
import org.example.copo.service.FacultyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/faculties")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class FacultyController {

    private final FacultyService facultyService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<Faculty>> getAllFaculties() {
        return ResponseEntity.ok(facultyService.getAllFaculties());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Faculty> createFaculty(@RequestBody Faculty faculty) {
        return ResponseEntity.ok(facultyService.createFaculty(faculty));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<Faculty> updateFaculty(@PathVariable String id, @RequestBody Faculty faculty) {
        return ResponseEntity.ok(facultyService.updateFaculty(id, faculty));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFaculty(@PathVariable String id) {
        facultyService.deleteFaculty(id);
        return ResponseEntity.ok().build();
    }
}
