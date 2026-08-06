package org.example.copo.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

@Entity
@Table(name = "StudentAssessmentMarks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StudentAssessmentMarks {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank
    @Column(name = "student_id", nullable = false, length = 9)
    private String studentId;

    @NotNull
    @Column(name = "question_id", nullable = false)
    private Integer questionId;

    @NotNull
    @PositiveOrZero
    @Column(name = "marks_obtained", nullable = false)
    private Double marksObtained;
}
