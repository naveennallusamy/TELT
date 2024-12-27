package com.telt.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;

@Entity
@Data
@Table(name = "tenant")
public class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tenantId;

    @NotNull(message = "Tenant Name must not be null")
    @NotEmpty(message = "Tenant Name must not be empty")
    @Column(nullable = false, unique = true)
    private String tenantName;

    private String description;

    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt = new Date();
}

