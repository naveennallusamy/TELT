package com.telt.dto.access;

import com.telt.constants.AccessMode;

import java.util.UUID;

/**
 * Omitted (null) fields are left unchanged.
 */
public record UpdateMemberRequest(AccessMode accessMode, UUID departmentId) {
}
