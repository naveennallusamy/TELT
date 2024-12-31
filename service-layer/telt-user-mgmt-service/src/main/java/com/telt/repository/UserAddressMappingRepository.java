package com.telt.repository;


import com.telt.entity.user.UserAddressAssoc;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAddressMappingRepository extends JpaRepository<UserAddressAssoc, Long> {
}
