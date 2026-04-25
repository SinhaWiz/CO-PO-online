package org.example.copo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Student")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Student {

    @Id
    @Column(length = 9)
    private String id;

    @Column(nullable = false)
    private Integer batch;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(unique = true, length = 100)
    private String email;

    @Column(length = 3)
    private String department;

    @Column(length = 11)
    private String programme;
}