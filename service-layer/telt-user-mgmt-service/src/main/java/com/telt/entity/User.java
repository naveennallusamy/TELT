package com.telt.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Getter
@Setter
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private Long mobileNumber;

    @Column(nullable = false)
    private String password;

    @ManyToOne(cascade = {CascadeType.MERGE})
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt = new Date();

    private String title;

    private String firstName;

    private String middleName;

    private String lastName;

    private String gender;

    private Date dateOfBirth;

    private String countryCode;

    private String nationality;

    @JsonManagedReference
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserAddressAssoc> userAddressDetails = new HashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return id != null && id.equals(user.id); // Compare only ID
    }

    @Override
    public int hashCode() {
        return Objects.hash(id); // Hash only ID
    }
}


