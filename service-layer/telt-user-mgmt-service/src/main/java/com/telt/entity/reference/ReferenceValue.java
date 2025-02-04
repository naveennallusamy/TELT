package com.telt.entity.reference;

import com.telt.util.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "reference_values", uniqueConstraints = {@UniqueConstraint(columnNames = {"referenceType", "referenceValue"})})
@Getter
@Setter
public class ReferenceValue extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String referenceType;

    private String referenceValue;

    @ColumnDefault("'Active'")
    private String status;
}
