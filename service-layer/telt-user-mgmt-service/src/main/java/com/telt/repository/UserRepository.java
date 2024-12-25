package com.telt.repository;

import com.telt.entity.User;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends BaseRepository<User, Long> {
    Optional<User> findByEmailOrMobile(String email, String mobile);
/*    Optional<User> findByEmailAndTenantId(String email, Long tenantId);
    Optional<User> findByMobileNumberAndTenantId(String mobileNumber, Long tenantId);
    Optional<User> findByEmail(String email);
    Optional<User> findByMobileNumber(String mobileNumber);*/
}
