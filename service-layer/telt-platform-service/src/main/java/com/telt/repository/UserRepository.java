package com.telt.repository;

import com.telt.entity.user.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserRepository extends BaseRepository<User, UUID> {

    /**
     * Global (not tenant-scoped) lookup used only by unauthenticated login / password-reset flows.
     */
    @Query("select u from User u where lower(u.email) = lower(:identifier) or u.username = :identifier "
            + "or u.mobileNumber = :mobile")
    List<User> findByLoginIdentifier(@Param("identifier") String identifier, @Param("mobile") Long mobile);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByMobileNumber(Long mobileNumber);

    boolean existsByUsername(String username);

    boolean existsByRole_Name(String roleName);
}
