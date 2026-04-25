package org.example.copo.entity;

import jakarta.persistence.*;
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

    @Column(name = "faculty_id", nullable = false, length = 20)
    private String facultyId;

    @Id
    @Column(name = "course_code", length = 20)
    private String courseCode;

    @Id
    @Column(length = 11)
    private String programme;

    @Id
    @Column(name = "academic_year", length = 9)
    private String academicYear;

    @Id
    @Column(length = 3)
    private String department;
}
