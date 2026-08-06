package org.example.copo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "CourseAssessmentSection")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CourseAssessmentSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "course_code", nullable = false)
    private String courseCode;

    @Column(nullable = false)
    private String programme;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(name = "section_order", nullable = false)
    private Integer sectionOrder;
}
