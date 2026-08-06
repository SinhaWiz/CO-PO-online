package org.example.copo.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Table(name = "CO")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank
    @Column(name = "co_number", nullable = false, length = 10)
    private String coNumber;
}
