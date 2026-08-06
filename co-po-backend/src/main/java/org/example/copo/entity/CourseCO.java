package org.example.copo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "Course_CO")
@IdClass(CourseCO.CourseCOId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CourseCO {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class CourseCOId implements Serializable {
        private String courseCode;
        private String programme;
        private Integer coId;
    }

    @Id
    @Column(name = "course_code")
    private String courseCode;

    @Id
    @Column(name = "programme")
    private String programme;

    @Id
    @Column(name = "co_id")
    private Integer coId;
}
