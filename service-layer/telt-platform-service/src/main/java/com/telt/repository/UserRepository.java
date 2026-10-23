package com.telt.repository;

import com.telt.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Users are global identities; tenant visibility is decided through {@code tenant_memberships}.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Global lookup used only by unauthenticated login / password-reset flows.
     */
    @Query("select u from User u where lower(u.email) = lower(:identifier) or u.username = :identifier "
            + "or u.mobileNumber = :mobile")
    List<User> findByLoginIdentifier(@Param("identifier") String identifier, @Param("mobile") Long mobile);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByMobileNumber(Long mobileNumber);

    boolean existsByUsername(String username);

    boolean existsByPlatformRole(String platformRole);
}
