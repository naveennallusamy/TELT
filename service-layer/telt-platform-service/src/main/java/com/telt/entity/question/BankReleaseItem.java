package com.telt.entity.question;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "bank_release_items")
public class BankReleaseItem {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID itemId;

    @Column(nullable = false, updatable = false)
    private UUID releaseId;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private UUID questionId;

    @Column(nullable = false, updatable = false)
    private int versionNo;
}
