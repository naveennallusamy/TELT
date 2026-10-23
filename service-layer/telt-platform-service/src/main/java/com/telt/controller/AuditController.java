package com.telt.controller;

import com.telt.dto.access.AuditEventResponse;
import com.telt.repository.AuditEventRepository;
import com.telt.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Latest 200 audit events of the active tenant (all tenants for super admins).
 */
@RestController
@RequestMapping("/audit-events")
@RequiredArgsConstructor
@PreAuthorize("hasPermission('TENANT', 'AUDIT_VIEW')")
public class AuditController {

    private final AuditEventRepository auditEventRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public List<AuditEventResponse> list() {
        var events = TenantContext.isSuperAdmin() && TenantContext.getCurrentTenant() == null
                ? auditEventRepository.findTop200ByOrderByOccurredAtDesc()
                : auditEventRepository.findTop200ByTenantIdOrderByOccurredAtDesc(TenantContext.requireCurrentTenant());
        return events.stream().map(AuditEventResponse::from).toList();
    }
}
