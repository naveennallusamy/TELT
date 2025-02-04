package com.telt.repository;

import com.telt.entity.organization.OrganizationDetails;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizationRepository extends BaseRepository<OrganizationDetails, Long> {
}
