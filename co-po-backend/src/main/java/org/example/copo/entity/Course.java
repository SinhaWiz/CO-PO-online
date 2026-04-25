package org.example.copo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "Course")
@IdClass(Course.CourseId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Course {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class CourseId implements Serializable {
        private String courseCode;
        private String programme;
    }

    @Id
    @Column(name = "course_code", length = 20)
    private String courseCode;

    @Id
    @Column(length = 11)
    private String programme;

    @Column(name = "course_name", nullable = false, length = 100)
    private String courseName;

    @Column(nullable = false)
    private Double credits;

    @Column(nullable = false, length = 3)
    private String department;
}