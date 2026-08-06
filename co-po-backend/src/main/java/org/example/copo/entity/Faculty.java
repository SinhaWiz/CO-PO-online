package org.example.copo.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Table(name = "Faculty")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Faculty {

    @Id
    @NotBlank
    @Column(length = 20)
    private String id;

    @NotBlank
    @Column(nullable = false, length = 50)
    private String shortname;

    @NotBlank
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @NotBlank
    @Email
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    // Write-only: accepted on create/update requests but never serialized back out.
    // Faculty.password used to come back verbatim (bcrypt hash) in every list/detail response.
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(nullable = false, length = 100)
    private String password;
}