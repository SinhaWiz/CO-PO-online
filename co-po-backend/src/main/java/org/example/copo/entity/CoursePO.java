package org.example.copo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "Course_PO")
@IdClass(CoursePO.CoursePOId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CoursePO {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class CoursePOId implements Serializable {
        private String courseCode;
        private String programme;
        private Integer poId;
    }

    @Id
    @Column(name = "course_code")
    private String courseCode;

    @Id
    @Column(name = "programme")
    private String programme;

    @Id
    @Column(name = "po_id")
    private Integer poId;
}
