package org.example.copo.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "CourseAssignment")
@IdClass(CourseAssignment.CourseAssignmentId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CourseAssignment {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class CourseAssignmentId implements Serializable {
        private String courseCode;
        private String programme;
        private String academicYear;
        private String department;
    }

    @NotBlank
    @Column(name = "faculty_id", nullable = false, length = 20)
    private String facultyId;

    @Id
    @NotBlank
    @Column(name = "course_code", length = 20)
    private String courseCode;

    @Id
    @NotBlank
    @Column(length = 11)
    private String programme;

    @Id
    @NotBlank
    @Column(name = "academic_year", length = 9)
    private String academicYear;

    @Id
    @NotBlank
    @Column(length = 3)
    private String department;
}
