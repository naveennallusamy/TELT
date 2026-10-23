package com.telt.entity.user;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.telt.constants.UserStatus;
import com.telt.util.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "users")
public class User extends AuditableEntity {
    public static final String PLATFORM_SUPER_ADMIN = "SUPER_ADMIN";

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private Long mobileNumber;

    @Column(nullable = false)
    private String password;

    /**
     * {@code SUPER_ADMIN} for platform administrators; {@code null} for tenant members.
     */
    private String platformRole;

    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(nullable = false)
    private int failedLoginAttempts;

    private Instant lockedUntil;

    @Column(nullable = false)
    private boolean passwordChangeRequired;

    private Instant passwordChangedAt;

    private Instant lastLoginAt;

    @JsonManagedReference
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private UserPersonalInfo personalInfo;

    @JsonManagedReference
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserAddressAssoc> userAddressDetails = new HashSet<>();

    public User(String username, String email, long mobileNumber) {
        this.username = username;
        this.email = email;
        this.mobileNumber = mobileNumber;
    }

    public boolean isSuperAdmin() {
        return PLATFORM_SUPER_ADMIN.equals(platformRole);
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return id != null && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
