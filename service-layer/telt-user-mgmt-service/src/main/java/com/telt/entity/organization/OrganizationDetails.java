package com.telt.entity.organization;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.telt.entity.tenant.Tenant;
import com.telt.util.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "organization_dtls")
public class OrganizationDetails extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String organizationName;

    private String website;

    private Long officePhone;

    @ManyToOne
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @JsonManagedReference
    @OneToOne(mappedBy = "organizationDetails", cascade = CascadeType.ALL, orphanRemoval = true)
    private OrgAddressAssoc orgAddress;
}
