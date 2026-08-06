package org.example.copo.service;

import lombok.RequiredArgsConstructor;
import org.example.copo.entity.CourseAssignmentThreshold;
import org.example.copo.repository.CourseAssignmentThresholdRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-course-assignment threshold overrides. The desktop app's own fallback chain here
 * was inconsistently wired (some screens read the global Thresholds table, most report
 * code went straight to a hardcoded literal) - this documents one clear chain instead:
 * per-assignment row, if one exists -> the global Thresholds table -> a hardcoded
 * literal, used only if that table is somehow empty (in practice it never is, since
 * AdminConfigurationService seeds it on startup).
 */
@Service
@RequiredArgsConstructor
public class CourseAssignmentThresholdService {

    private static final double DEFAULT_CO_INDIVIDUAL = 60.0;
    private static final double DEFAULT_PO_INDIVIDUAL = 40.0;
    private static final double DEFAULT_CO_COHORT = 50.0;
    private static final double DEFAULT_PO_COHORT = 50.0;

    private final CourseAssignmentThresholdRepository thresholdRepository;
    private final JdbcTemplate jdbcTemplate;

    public record ThresholdsDto(double coIndividual, double poIndividual, double coCohort, double poCohort) {}

    // Called when a CourseAssignment is created, so a threshold row always exists for
    // it from day one rather than only appearing the first time someone edits it.
    @Transactional
    public void seedDefaultIfMissing(String courseCode, String programme, String academicYear, String department) {
        boolean exists = thresholdRepository
            .findByCourseCodeAndProgrammeAndAcademicYearAndDepartment(courseCode, programme, academicYear, department)
            .isPresent();
        if (exists) {
            return;
        }

        ThresholdsDto defaults = readGlobalThresholds();
        CourseAssignmentThreshold threshold = new CourseAssignmentThreshold();
        threshold.setCourseCode(courseCode);
        threshold.setProgramme(programme);
        threshold.setAcademicYear(academicYear);
        threshold.setDepartment(department);
        threshold.setCoIndividual(defaults.coIndividual());
        threshold.setPoIndividual(defaults.poIndividual());
        threshold.setCoCohort(defaults.coCohort());
        threshold.setPoCohort(defaults.poCohort());
        thresholdRepository.save(threshold);
    }

    public ThresholdsDto getThresholds(String courseCode, String programme, String academicYear, String department) {
        return thresholdRepository
            .findByCourseCodeAndProgrammeAndAcademicYearAndDepartment(courseCode, programme, academicYear, department)
            .map(t -> new ThresholdsDto(t.getCoIndividual(), t.getPoIndividual(), t.getCoCohort(), t.getPoCohort()))
            .orElseGet(this::readGlobalThresholds);
    }

    @Transactional
    public ThresholdsDto updateThresholds(
        String courseCode, String programme, String academicYear, String department, ThresholdsDto dto
    ) {
        CourseAssignmentThreshold threshold = thresholdRepository
            .findByCourseCodeAndProgrammeAndAcademicYearAndDepartment(courseCode, programme, academicYear, department)
            .orElseGet(() -> {
                CourseAssignmentThreshold t = new CourseAssignmentThreshold();
                t.setCourseCode(courseCode);
                t.setProgramme(programme);
                t.setAcademicYear(academicYear);
                t.setDepartment(department);
                return t;
            });

        threshold.setCoIndividual(dto.coIndividual());
        threshold.setPoIndividual(dto.poIndividual());
        threshold.setCoCohort(dto.coCohort());
        threshold.setPoCohort(dto.poCohort());
        CourseAssignmentThreshold saved = thresholdRepository.save(threshold);

        return new ThresholdsDto(saved.getCoIndividual(), saved.getPoIndividual(), saved.getCoCohort(), saved.getPoCohort());
    }

    private ThresholdsDto readGlobalThresholds() {
        Map<String, Double> values = new HashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT type, percentage FROM Thresholds");
        for (Map<String, Object> row : rows) {
            Object type = row.get("type");
            Object percentage = row.get("percentage");
            if (type != null && percentage instanceof Number number) {
                values.put(type.toString(), number.doubleValue());
            }
        }

        return new ThresholdsDto(
            values.getOrDefault("CO_INDIVIDUAL", DEFAULT_CO_INDIVIDUAL),
            values.getOrDefault("PO_INDIVIDUAL", DEFAULT_PO_INDIVIDUAL),
            values.getOrDefault("CO_COHORTSET", DEFAULT_CO_COHORT),
            values.getOrDefault("PO_COHORTSET", DEFAULT_PO_COHORT)
        );
    }
}
