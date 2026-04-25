package org.example.copo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "Enrollment")
@IdClass(Enrollment.EnrollmentId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Enrollment {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class EnrollmentId implements Serializable {
        private String studentId;
        private String courseId;
        private String programme;
        private String academicYear;
    }

    @Id
    @Column(name = "student_id", length = 9)
    private String studentId;

    @Id
    @Column(name = "course_id", length = 20)
    private String courseId;

    @Id
    @Column(length = 11)
    private String programme;

    @Id
    @Column(name = "academic_year", length = 9)
    private String academicYear;
}
