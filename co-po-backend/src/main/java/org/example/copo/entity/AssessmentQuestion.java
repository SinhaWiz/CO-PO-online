package org.example.copo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "AssessmentQuestion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "assessment_id", nullable = false)
    private Integer assessmentId;

    @Column(nullable = false, length = 20)
    private String title;

    @Column(nullable = false)
    private Double marks;

    @Column(name = "co_id")
    private Integer coId;

    @Column(name = "po_id")
    private Integer poId;
}
