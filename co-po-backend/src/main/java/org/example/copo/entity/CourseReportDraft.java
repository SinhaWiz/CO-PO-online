package org.example.copo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "CourseReportDraft")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CourseReportDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "course_code", nullable = false)
    private String courseCode;

    @Column(nullable = false)
    private String programme;

    @Column(name = "academic_year", nullable = false)
    private String academicYear;

    @Column(nullable = false)
    private String department;

    @Column(name = "faculty_id", nullable = false)
    private String facultyId;

    @Lob
    @Column(name = "form_json", nullable = false)
    private String formJson;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
