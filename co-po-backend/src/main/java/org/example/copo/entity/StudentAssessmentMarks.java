package org.example.copo.entity;

import jakarta.persistence.*;
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

    @Column(name = "student_id", nullable = false, length = 9)
    private String studentId;

    @Column(name = "question_id", nullable = false)
    private Integer questionId;

    @Column(name = "marks_obtained", nullable = false)
    private Double marksObtained;
}
