package org.example.copo.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Table(name = "PO")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank
    @Column(name = "po_number", nullable = false, unique = true, length = 10)
    private String poNumber;
}
