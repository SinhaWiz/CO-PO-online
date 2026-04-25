package org.example.copo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Faculty")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Faculty {

    @Id
    @Column(length = 20)
    private String id;

    @Column(nullable = false, length = 50)
    private String shortname;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 100)
    private String password;
}