package org.example.copo.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Entity
@Table(name = "Student")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Student {

    @Id
    @NotBlank
    @Column(length = 9)
    private String id;

    @NotNull
    @Column(nullable = false)
    private Integer batch;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String name;

    @Email
    @Column(unique = true, length = 100)
    private String email;

    @Column(length = 3)
    private String department;

    @Column(length = 11)
    private String programme;
}