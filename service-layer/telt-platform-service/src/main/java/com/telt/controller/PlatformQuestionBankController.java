package com.telt.controller;

import com.telt.dto.platform.*;
import com.telt.service.PlatformBankService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Super-admin release and grant management for platform banks. Platform banks and questions are authored through
 * {@code /question-banks} and {@code /questions} with a super-admin token.
 */
@RestController
@RequestMapping("/platform")
@RequiredArgsConstructor
public class PlatformQuestionBankController {

    private final PlatformBankService platformBankService;

    @PostMapping("/question-banks/{bankId}/releases")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SUPER_ADMIN') and hasPermission(null, 'PLATFORM_QBANK_MANAGE')")
    public ReleaseResponse release(@PathVariable UUID bankId, @Valid @RequestBody(required = false) ReleaseRequest request) {
        return platformBankService.publishRelease(bankId, request);
    }

    @GetMapping("/question-banks/{bankId}/releases")
    @PreAuthorize("hasAuthority('SUPER_ADMIN') and hasPermission(null, 'PLATFORM_QBANK_MANAGE')")
    public List<ReleaseResponse> releases(@PathVariable UUID bankId) {
        return platformBankService.releases(bankId);
    }

    @PostMapping("/grants")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SUPER_ADMIN') and hasPermission(null, 'PLATFORM_QBANK_GRANT')")
    public GrantResponse grant(@Valid @RequestBody CreateGrantRequest request) {
        return platformBankService.grant(request);
    }

    @GetMapping("/grants")
    @PreAuthorize("hasAuthority('SUPER_ADMIN') and hasPermission(null, 'PLATFORM_QBANK_GRANT')")
    public List<GrantResponse> grants(@RequestParam(required = false) UUID tenantId,
                                      @RequestParam(required = false) UUID bankId) {
        return platformBankService.grants(tenantId, bankId);
    }

    @PutMapping("/grants/{grantId}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN') and hasPermission(null, 'PLATFORM_QBANK_GRANT')")
    public GrantResponse update(@PathVariable UUID grantId, @Valid @RequestBody GrantSettings settings) {
        return platformBankService.update(grantId, settings);
    }

    @PostMapping("/grants/{grantId}/revoke")
    @PreAuthorize("hasAuthority('SUPER_ADMIN') and hasPermission(null, 'PLATFORM_QBANK_GRANT')")
    public GrantResponse revoke(@PathVariable UUID grantId) {
        return platformBankService.revoke(grantId);
    }
}
