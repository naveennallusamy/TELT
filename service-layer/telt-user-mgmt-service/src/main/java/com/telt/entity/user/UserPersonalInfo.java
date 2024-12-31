package com.telt.entity.user;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.telt.util.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Entity
@Getter
@Setter
@Table(name = "user_personal_info")
public class UserPersonalInfo extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String firstName;

    private String middleName;

    private String lastName;

    private String gender;

    @Temporal(TemporalType.DATE)
    private Date dateOfBirth;

    private String nationality;

    private String designation;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference
    private User user;
}