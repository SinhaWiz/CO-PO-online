package org.example.copo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "CourseAssignmentThreshold")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CourseAssignmentThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "course_code", nullable = false)
    private String courseCode;

    @Column(nullable = false)
    private String programme;

    @Column(name = "academic_year", nullable = false)
    private String academicYear;

    @Column(nullable = false)
    private String department;

    @Column(name = "co_individual")
    private Double coIndividual;

    @Column(name = "po_individual")
    private Double poIndividual;

    @Column(name = "co_cohort")
    private Double coCohort;

    @Column(name = "po_cohort")
    private Double poCohort;
}
