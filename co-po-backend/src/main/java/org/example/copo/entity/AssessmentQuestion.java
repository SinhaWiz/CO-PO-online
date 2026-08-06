package org.example.copo.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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

    @NotNull
    @Column(name = "assessment_id", nullable = false)
    private Integer assessmentId;

    @NotBlank
    @Column(nullable = false, length = 20)
    private String title;

    @NotNull
    @Positive
    @Column(nullable = false)
    private Double marks;

    @Column(name = "co_id")
    private Integer coId;

    // PO mapping lives in AssessmentQuestion_PO now (a question can map to several POs,
    // not just one) - see AssessmentQuestionPO. The AssessmentQuestion table still has
    // an unused po_id column underneath this entity; left in place rather than dropped
    // sight-unseen by a migration, since it was never mapped here to begin with once
    // this change lands.
}
