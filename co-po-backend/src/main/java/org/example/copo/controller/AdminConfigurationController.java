package org.example.copo.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.example.copo.service.AdminConfigurationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/config")
@CrossOrigin(origins = "*", maxAge = 3600)
@Validated
public class AdminConfigurationController {

    private final AdminConfigurationService configService;

    public AdminConfigurationController(AdminConfigurationService configService) {
        this.configService = configService;
    }

    public record ThresholdsRequest(
        double coIndividual,
        double poIndividual,
        double coCohort,
        double poCohort
    ) {}

    public record CulminationSaveRequest(
        @NotBlank String programme,
        List<String> courseCodes
    ) {}

    public record GraduatingSaveRequest(
        @NotBlank String programme,
        @NotNull Integer batch,
        List<String> graduatingStudentIds,
        Map<String, String> commentsByStudentId
    ) {}

    private static void validateThreshold(double value, String label) {
        if (value < 0 || value >= 100) {
            throw new IllegalArgumentException(label + " threshold must be between 0 and 99.99.");
        }
    }

    private static void validateAcademicYearBatch(Integer batch) {
        if (batch == null || batch < 0) {
            throw new IllegalArgumentException("Batch is invalid.");
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/thresholds")
    public ResponseEntity<AdminConfigurationService.ThresholdsDto> getThresholds() {
        return ResponseEntity.ok(configService.getThresholds());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/thresholds")
    public ResponseEntity<?> updateThresholds(@RequestBody ThresholdsRequest request) {
        validateThreshold(request.coIndividual(), "CO individual");
        validateThreshold(request.poIndividual(), "PO individual");
        validateThreshold(request.coCohort(), "CO cohort");
        validateThreshold(request.poCohort(), "PO cohort");

        configService.updateThresholds(new AdminConfigurationService.ThresholdsDto(
            request.coIndividual(),
            request.poIndividual(),
            request.coCohort(),
            request.poCohort()
        ));
        return ResponseEntity.ok(Map.of("message", "Thresholds updated successfully."));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/culmination/programmes")
    public ResponseEntity<List<String>> getCulminationProgrammes() {
        return ResponseEntity.ok(configService.getDistinctProgrammesFromCourses());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/culmination/courses")
    public ResponseEntity<List<AdminConfigurationService.CourseDisplayDto>> getCoursesForProgramme(
        @RequestParam String programme
    ) {
        return ResponseEntity.ok(configService.getCoursesForProgramme(programme));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/culmination/selected")
    public ResponseEntity<List<String>> getSelectedCulminationCourses(@RequestParam String programme) {
        return ResponseEntity.ok(configService.getCulminationCourses(programme));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/culmination/save")
    public ResponseEntity<AdminConfigurationService.CulminationSaveResult> saveCulminationCourses(
        @Valid @RequestBody CulminationSaveRequest request
    ) {
        List<String> codes = request.courseCodes() == null ? List.of() : new ArrayList<>(request.courseCodes());
        AdminConfigurationService.CulminationSaveResult result = configService.saveCulminationCourses(request.programme(), codes);
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/graduating/programmes")
    public ResponseEntity<List<String>> getGraduatingProgrammes() {
        return ResponseEntity.ok(configService.getDistinctProgrammesFromStudents());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/graduating/batches")
    public ResponseEntity<List<Integer>> getBatchesByProgramme(@RequestParam String programme) {
        return ResponseEntity.ok(configService.getBatchesForProgramme(programme));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/graduating/students")
    public ResponseEntity<List<AdminConfigurationService.StudentRowDto>> getStudentsByProgrammeAndBatch(
        @RequestParam String programme,
        @RequestParam int batch
    ) {
        List<AdminConfigurationService.StudentRowDto> rows = configService.getStudentsByProgrammeAndBatch(programme, batch);
        return ResponseEntity.ok(configService.sortStudentsById(rows));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/graduating/selected")
    public ResponseEntity<List<String>> getGraduatingSelectedIds(
        @RequestParam String programme,
        @RequestParam int batch
    ) {
        return ResponseEntity.ok(configService.getGraduatingStudentIds(programme, batch));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/graduating/save")
    public ResponseEntity<AdminConfigurationService.GraduatingSaveResult> saveGraduatingStudents(
        @Valid @RequestBody GraduatingSaveRequest request
    ) {
        validateAcademicYearBatch(request.batch());

        List<String> ids = request.graduatingStudentIds() == null ? List.of() : request.graduatingStudentIds();
        Map<String, String> comments = request.commentsByStudentId() == null ? new HashMap<>() : request.commentsByStudentId();

        AdminConfigurationService.GraduatingSaveResult result = configService.saveGraduatingStudents(
            request.programme(),
            request.batch(),
            ids,
            comments
        );
        return ResponseEntity.ok(result);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleValidationError(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }
}
