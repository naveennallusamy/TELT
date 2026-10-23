package com.telt.entity.user;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.telt.util.AuditableEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@Table(name = "user_personal_info")
public class UserPersonalInfo extends AuditableEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private String title;

    private String firstName;

    private String middleName;

    private String lastName;

    private String gender;

    private LocalDate dateOfBirth;

    private String nationality;

    private String designation;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference
    private User user;
}