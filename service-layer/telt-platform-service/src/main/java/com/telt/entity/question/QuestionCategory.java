package com.telt.entity.question;

import com.telt.util.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/**
 * Node of a tenant's subject/topic taxonomy; {@code path} works like {@code Department.path}.
 */
@Entity
@Getter
@Setter
@Table(name = "question_categories")
public class QuestionCategory extends AuditableEntity {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID categoryId;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(updatable = false)
    private UUID parentCategoryId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String path;
}
