package com.telt.entity.reference;

import com.telt.util.AuditableEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "reference_values", uniqueConstraints = {@UniqueConstraint(columnNames = {"referenceType", "referenceValue"})})
@Getter
@Setter
public class ReferenceValue extends AuditableEntity {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private String referenceType;

    private String referenceValue;

    @ColumnDefault("'Active'")
    private String status;
}
