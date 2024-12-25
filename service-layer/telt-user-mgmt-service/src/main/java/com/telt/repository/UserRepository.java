package com.telt.repository;

import com.telt.entity.User;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends BaseRepository<User, String> {
    Optional<User> findByEmailOrMobileNumber(String email, Long mobileNumber);

    /*    Optional<User> findByEmailAndTenantId(String email, Long tenantId);
        Optional<User> findByMobileNumberAndTenantId(String mobileNumber, Long tenantId);*/
    Optional<User> findByEmail(String email);

    Optional<User> findByMobileNumber(Long mobileNumber);

    boolean existsByUsername(String username);

    Optional<User> findByEmailOrMobileNumberOrUsername(String email, Long mobileNumber, String username);
}
