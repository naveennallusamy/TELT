package com.telt.repository;

import com.telt.entity.User;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends BaseRepository<User, String> {
    Optional<User> findByEmailOrMobileNumber(String email, String mobileNumber);

    /*    Optional<User> findByEmailAndTenantId(String email, Long tenantId);
        Optional<User> findByMobileNumberAndTenantId(String mobileNumber, Long tenantId);*/
    Optional<User> findByEmail(String email);

    Optional<User> findByMobileNumber(String mobileNumber);
}
