package org.example.copo.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AdminConfigurationService {

    private final JdbcTemplate jdbcTemplate;

    public AdminConfigurationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record ThresholdsDto(double coIndividual, double poIndividual, double coCohort, double poCohort) {}
    public record CourseDisplayDto(String courseCode, String courseName, String display) {}
    public record CulminationSaveResult(boolean saved, List<String> missingPOs) {}
    public record StudentRowDto(String id, String name, String comment) {}
    public record GraduatingSaveResult(int graduatingCount, int commentedNonGraduatingCount) {}

    public ThresholdsDto getThresholds() {
        String sql = "SELECT type, percentage FROM Thresholds";
        Map<String, Double> values = new HashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        for (Map<String, Object> row : rows) {
            Object type = row.get("type");
            Object percentage = row.get("percentage");
            if (type != null && percentage instanceof Number number) {
                values.put(type.toString(), number.doubleValue());
            }
        }

        return new ThresholdsDto(
            values.getOrDefault("CO_INDIVIDUAL", 60.0),
            values.getOrDefault("PO_INDIVIDUAL", 40.0),
            values.getOrDefault("CO_COHORTSET", 50.0),
            values.getOrDefault("PO_COHORTSET", 50.0)
        );
    }

    @Transactional
    public void updateThresholds(ThresholdsDto dto) {
        upsertThreshold("CO_INDIVIDUAL", dto.coIndividual());
        upsertThreshold("PO_INDIVIDUAL", dto.poIndividual());
        upsertThreshold("CO_COHORTSET", dto.coCohort());
        upsertThreshold("PO_COHORTSET", dto.poCohort());
    }

    private void upsertThreshold(String type, double value) {
        int updated = jdbcTemplate.update("UPDATE Thresholds SET percentage=? WHERE type=?", value, type);
        if (updated == 0) {
            jdbcTemplate.update("INSERT INTO Thresholds (type, percentage) VALUES (?, ?)", type, value);
        }
    }

    public List<String> getDistinctProgrammesFromCourses() {
        String sql = "SELECT DISTINCT programme FROM Course WHERE programme IS NOT NULL AND programme<>'' ORDER BY programme";
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1));
    }

    public List<CourseDisplayDto> getCoursesForProgramme(String programme) {
        String sql = "SELECT course_code, course_name FROM Course WHERE programme=? ORDER BY course_code";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String code = rs.getString("course_code");
            String name = rs.getString("course_name");
            return new CourseDisplayDto(code, name, code + " - " + name);
        }, programme);
    }

    public List<String> getCulminationCourses(String programme) {
        String sql = "SELECT course_code FROM CulminationCourse WHERE programme=? ORDER BY course_code";
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1), programme);
    }

    public List<String> getMissingPOsForCourses(String programme, List<String> courseCodes, int totalPOs) {
        if (courseCodes == null || courseCodes.isEmpty()) {
            List<String> missing = new ArrayList<>();
            for (int i = 1; i <= Math.min(totalPOs, 12); i++) {
                missing.add("PO" + i);
            }
            return missing;
        }

        String placeholders = String.join(",", java.util.Collections.nCopies(courseCodes.size(), "?"));
        String sql = "SELECT DISTINCT po.po_number FROM Course_PO cp JOIN PO po ON cp.po_id = po.id WHERE cp.programme=? AND cp.course_code IN (" + placeholders + ")";

        List<Object> params = new ArrayList<>();
        params.add(programme);
        params.addAll(courseCodes);

        Set<String> present = new LinkedHashSet<>(jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1), params.toArray()));

        List<String> missing = new ArrayList<>();
        for (int i = 1; i <= totalPOs; i++) {
            String po = "PO" + i;
            if (!present.contains(po)) {
                missing.add(po);
            }
        }
        return missing;
    }

    @Transactional
    public CulminationSaveResult saveCulminationCourses(String programme, List<String> courseCodes) {
        List<String> normalized = courseCodes == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(courseCodes));

        List<String> missing = getMissingPOsForCourses(programme, normalized, 12);
        if (!missing.isEmpty()) {
            return new CulminationSaveResult(false, missing);
        }

        jdbcTemplate.update("DELETE FROM CulminationCourse WHERE programme=?", programme);
        for (String code : normalized) {
            jdbcTemplate.update(
                "INSERT INTO CulminationCourse (course_code, programme) VALUES (?, ?)",
                code,
                programme
            );
        }

        return new CulminationSaveResult(true, List.of());
    }

    public List<String> getDistinctProgrammesFromStudents() {
        String sql = "SELECT DISTINCT programme FROM Student WHERE programme IS NOT NULL AND programme<>'' ORDER BY programme";
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1));
    }

    public List<Integer> getBatchesForProgramme(String programme) {
        String sql = "SELECT DISTINCT batch FROM Student WHERE programme=? ORDER BY batch";
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getInt(1), programme);
    }

    public List<StudentRowDto> getStudentsByProgrammeAndBatch(String programme, int batch) {
        String sql = """
            SELECT s.id, s.name, COALESCE(ng.comment, '') AS comment
            FROM Student s
            LEFT JOIN NonGraduatingStudentComment ng
              ON ng.student_id = s.id AND ng.programme = s.programme AND ng.batch = s.batch
            WHERE s.programme = ? AND s.batch = ?
            ORDER BY s.id
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) ->
            new StudentRowDto(rs.getString("id"), rs.getString("name"), rs.getString("comment")),
            programme,
            batch
        );
    }

    public List<String> getGraduatingStudentIds(String programme, int batch) {
        String sql = "SELECT gs.id FROM GraduatingStudent gs JOIN Student s ON s.id=gs.id WHERE s.programme=? AND s.batch=? ORDER BY gs.id";
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1), programme, batch);
    }

    @Transactional
    public GraduatingSaveResult saveGraduatingStudents(
        String programme,
        int batch,
        List<String> graduatingStudentIds,
        Map<String, String> commentsByStudentId
    ) {
        List<String> cohortIds = jdbcTemplate.query(
            "SELECT id FROM Student WHERE programme=? AND batch=?",
            (rs, rowNum) -> rs.getString(1),
            programme,
            batch
        );

        Set<String> cohortIdSet = new LinkedHashSet<>(cohortIds);
        Set<String> graduatingIds = new LinkedHashSet<>();
        if (graduatingStudentIds != null) {
            for (String id : graduatingStudentIds) {
                if (cohortIdSet.contains(id)) {
                    graduatingIds.add(id);
                }
            }
        }

        jdbcTemplate.update(
            "DELETE FROM GraduatingStudent WHERE id IN (SELECT id FROM Student WHERE programme=? AND batch=?)",
            programme,
            batch
        );

        for (String id : graduatingIds) {
            jdbcTemplate.update("INSERT IGNORE INTO GraduatingStudent (id) VALUES (?)", id);
        }

        for (String id : graduatingIds) {
            jdbcTemplate.update(
                "DELETE FROM NonGraduatingStudentComment WHERE student_id=? AND programme=? AND batch=?",
                id,
                programme,
                batch
            );
        }

        Map<String, String> safeComments = commentsByStudentId == null ? Map.of() : commentsByStudentId;

        int commented = 0;
        for (String studentId : cohortIdSet) {
            if (graduatingIds.contains(studentId)) {
                continue;
            }

            String comment = safeComments.get(studentId);
            if (comment != null) {
                comment = comment.trim();
            }

            if (comment != null && !comment.isEmpty()) {
                jdbcTemplate.update(
                    """
                    INSERT INTO NonGraduatingStudentComment (student_id, programme, batch, comment)
                    VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE comment=VALUES(comment)
                    """,
                    studentId,
                    programme,
                    batch,
                    comment
                );
                commented++;
            } else {
                jdbcTemplate.update(
                    "DELETE FROM NonGraduatingStudentComment WHERE student_id=? AND programme=? AND batch=?",
                    studentId,
                    programme,
                    batch
                );
            }
        }

        return new GraduatingSaveResult(graduatingIds.size(), commented);
    }

    public List<StudentRowDto> sortStudentsById(List<StudentRowDto> students) {
        return students.stream().sorted(Comparator.comparing(StudentRowDto::id)).toList();
    }
}
