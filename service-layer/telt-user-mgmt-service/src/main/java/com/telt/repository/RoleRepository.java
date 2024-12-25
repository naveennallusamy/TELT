package com.telt.repository;

import com.telt.entity.Role;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends BaseRepository<Role, Long> {
    Role findByName(String name);
}
