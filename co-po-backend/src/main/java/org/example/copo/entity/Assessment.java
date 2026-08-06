package org.example.copo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Assessment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Assessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "section_id", nullable = false)
    private Integer sectionId;

    @Column(name = "academic_year", nullable = false, length = 9)
    private String academicYear;

    @Column(name = "total_marks")
    private Double totalMarks;
}
