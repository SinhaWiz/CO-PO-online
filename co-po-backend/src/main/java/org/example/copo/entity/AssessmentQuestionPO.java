package org.example.copo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "AssessmentQuestion_PO")
@IdClass(AssessmentQuestionPO.AssessmentQuestionPOId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentQuestionPO {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class AssessmentQuestionPOId implements Serializable {
        private Integer questionId;
        private Integer poId;
    }

    @Id
    @Column(name = "question_id")
    private Integer questionId;

    @Id
    @Column(name = "po_id")
    private Integer poId;
}
