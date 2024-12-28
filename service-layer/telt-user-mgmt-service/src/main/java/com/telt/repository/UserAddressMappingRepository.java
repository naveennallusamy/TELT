package com.telt.repository;


import com.telt.entity.UserAddressAssoc;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAddressMappingRepository extends JpaRepository<UserAddressAssoc, Long> {
}
