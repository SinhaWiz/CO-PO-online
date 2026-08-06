package org.example.copo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "EnrollmentAttendance")
@IdClass(EnrollmentAttendance.EnrollmentAttendanceId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentAttendance {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class EnrollmentAttendanceId implements Serializable {
        private String studentId;
        private String courseId;
        private String programme;
        private String academicYear;
    }

    @Id
    @Column(name = "student_id")
    private String studentId;

    @Id
    @Column(name = "course_id")
    private String courseId;

    @Id
    @Column(name = "programme")
    private String programme;

    @Id
    @Column(name = "academic_year")
    private String academicYear;

    @Column(name = "attendance_percentage")
    private Double attendancePercentage;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
