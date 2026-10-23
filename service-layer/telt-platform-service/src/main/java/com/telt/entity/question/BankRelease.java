package com.telt.entity.question;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "bank_releases")
public class BankRelease {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID releaseId;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private UUID bankId;

    @Column(nullable = false, updatable = false)
    private int releaseNo;

    @Column(updatable = false)
    private String notes;

    @Column(nullable = false, updatable = false)
    private int itemCount;

    @Column(nullable = false, updatable = false)
    private UUID publishedBy;

    @Column(nullable = false, updatable = false)
    private Instant publishedAt;
}
