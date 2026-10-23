package com.telt.repository;

import com.telt.entity.organization.OrganizationDetails;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OrganizationRepository extends BaseRepository<OrganizationDetails, UUID> {
}
