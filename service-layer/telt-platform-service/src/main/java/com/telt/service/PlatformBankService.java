package com.telt.service;

import com.telt.constants.GrantStatus;
import com.telt.constants.PlatformTenant;
import com.telt.constants.QuestionStatus;
import com.telt.constants.ReleasePolicy;
import com.telt.dto.platform.*;
import com.telt.entity.question.*;
import com.telt.error.ApiException;
import com.telt.repository.*;
import com.telt.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Super-admin side of platform question banks: frozen releases and grants to tenants.
 * Platform banks and questions themselves are authored through the regular question-bank API.
 */
@Service
@RequiredArgsConstructor
public class PlatformBankService {

    private final QuestionBankService bankService;
    private final QuestionBankRepository bankRepository;
    private final QuestionRepository questionRepository;
    private final BankReleaseRepository releaseRepository;
    private final BankReleaseItemRepository itemRepository;
    private final PlatformBankGrantRepository grantRepository;
    private final TenantRepository tenantRepository;
    private final AuditService auditService;

    /**
     * Freezes the approved version of every active question in the bank as the next release.
     */
    @Transactional
    public ReleaseResponse publishRelease(UUID bankId, ReleaseRequest request) {
        QuestionBank bank = bankService.loadActive(PlatformTenant.ID, bankId);
        List<Question> questions = questionRepository
                .findByTenantIdAndBankIdAndStatusAndApprovedVersionNotNullOrderByQuestionId(PlatformTenant.ID,
                        bank.getBankId(), QuestionStatus.ACTIVE);
        if (questions.isEmpty()) {
            throw ApiException.conflict("The bank has no approved questions to release");
        }
        BankRelease release = new BankRelease();
        release.setTenantId(PlatformTenant.ID);
        release.setBankId(bankId);
        release.setReleaseNo(releaseRepository.findFirstByBankIdOrderByReleaseNoDesc(bankId)
                .map(r -> r.getReleaseNo() + 1).orElse(1));
        release.setNotes(request == null ? null : request.notes());
        release.setItemCount(questions.size());
        release.setPublishedBy(TenantContext.requireCurrentUserId());
        release.setPublishedAt(Instant.now());
        releaseRepository.saveAndFlush(release);
        itemRepository.saveAll(questions.stream().map(q -> {
            BankReleaseItem item = new BankReleaseItem();
            item.setReleaseId(release.getReleaseId());
            item.setTenantId(PlatformTenant.ID);
            item.setQuestionId(q.getQuestionId());
            item.setVersionNo(q.getApprovedVersion());
            return item;
        }).toList());
        auditService.record(PlatformTenant.ID, "QBANK_RELEASED", "QUESTION_BANK", bankId,
                Map.of("releaseNo", release.getReleaseNo(), "items", questions.size()));
        return ReleaseResponse.from(release);
    }

    @Transactional(readOnly = true)
    public List<ReleaseResponse> releases(UUID bankId) {
        bankService.load(PlatformTenant.ID, bankId);
        return releaseRepository.findByBankIdOrderByReleaseNoDesc(bankId).stream().map(ReleaseResponse::from).toList();
    }

    @Transactional
    public GrantResponse grant(CreateGrantRequest request) {
        QuestionBank bank = bankService.loadActive(PlatformTenant.ID, request.bankId());
        if (PlatformTenant.is(request.tenantId()) || !tenantRepository.existsById(request.tenantId())) {
            throw ApiException.notFound("Tenant");
        }
        if (grantRepository.existsByTenantIdAndBankIdAndStatus(request.tenantId(), bank.getBankId(), GrantStatus.ACTIVE)) {
            throw ApiException.conflict("The tenant already has an active grant for this bank");
        }
        PlatformBankGrant grant = new PlatformBankGrant();
        grant.setTenantId(request.tenantId());
        grant.setBankId(bank.getBankId());
        apply(grant, request.settings());
        grantRepository.save(grant);
        auditService.record(request.tenantId(), "QBANK_GRANTED", "PLATFORM_GRANT", grant.getGrantId(),
                settingsAudit(bank, grant));
        return response(grant, bank);
    }

    @Transactional
    public GrantResponse update(UUID grantId, GrantSettings settings) {
        PlatformBankGrant grant = load(grantId);
        if (grant.getStatus() != GrantStatus.ACTIVE) {
            throw ApiException.conflict("Revoked grants cannot be changed");
        }
        apply(grant, settings);
        QuestionBank bank = bankService.load(PlatformTenant.ID, grant.getBankId());
        auditService.record(grant.getTenantId(), "QBANK_GRANT_UPDATED", "PLATFORM_GRANT", grantId,
                settingsAudit(bank, grant));
        return response(grant, bank);
    }

    /**
     * Stops future use of the bank. Exams already published keep their own copy of the questions.
     */
    @Transactional
    public GrantResponse revoke(UUID grantId) {
        PlatformBankGrant grant = load(grantId);
        if (grant.getStatus() == GrantStatus.ACTIVE) {
            grant.setStatus(GrantStatus.REVOKED);
            grant.setRevokedAt(Instant.now());
            auditService.record(grant.getTenantId(), "QBANK_GRANT_REVOKED", "PLATFORM_GRANT", grantId,
                    Map.of("bankId", grant.getBankId()));
        }
        return response(grant, bankService.load(PlatformTenant.ID, grant.getBankId()));
    }

    @Transactional(readOnly = true)
    public List<GrantResponse> grants(UUID tenantId, UUID bankId) {
        Map<UUID, QuestionBank> banks = new HashMap<>();
        return grantRepository.findAllByOrderByCreatedOn().stream()
                .filter(g -> tenantId == null || tenantId.equals(g.getTenantId()))
                .filter(g -> bankId == null || bankId.equals(g.getBankId()))
                .map(g -> response(g, banks.computeIfAbsent(g.getBankId(),
                        id -> bankService.load(PlatformTenant.ID, id))))
                .toList();
    }

    /**
     * Release a grant currently exposes: the pinned one, or the newest for {@code LATEST}.
     */
    Optional<BankRelease> effectiveRelease(PlatformBankGrant grant) {
        return grant.getReleasePolicy() == ReleasePolicy.PINNED
                ? releaseRepository.findById(grant.getReleaseId())
                : releaseRepository.findFirstByBankIdOrderByReleaseNoDesc(grant.getBankId());
    }

    GrantResponse response(PlatformBankGrant g, QuestionBank bank) {
        Optional<BankRelease> effective = g.getStatus() == GrantStatus.ACTIVE ? effectiveRelease(g) : Optional.empty();
        return new GrantResponse(g.getGrantId(), g.getTenantId(), g.getBankId(), bank.getName(), g.getReleasePolicy(),
                g.getReleaseId(), effective.map(BankRelease::getReleaseId).orElse(null),
                effective.map(BankRelease::getReleaseNo).orElse(null), g.isAllowClone(), g.isAnswerKeysVisible(),
                g.getValidFrom(), g.getValidUntil(), g.getStatus());
    }

    private void apply(PlatformBankGrant grant, GrantSettings settings) {
        if (settings.releasePolicy() == ReleasePolicy.PINNED) {
            if (settings.releaseId() == null) {
                throw ApiException.badRequest("releaseId is required for PINNED grants");
            }
            releaseRepository.findByReleaseIdAndBankId(settings.releaseId(), grant.getBankId())
                    .orElseThrow(() -> ApiException.notFound("Release"));
        } else if (settings.releaseId() != null) {
            throw ApiException.badRequest("releaseId must be empty for LATEST grants");
        }
        if (settings.allowClone() && !settings.answerKeysVisible()) {
            throw ApiException.badRequest("allowClone requires answerKeysVisible, because clones include the answer key");
        }
        if (settings.validFrom() != null && settings.validUntil() != null
                && !settings.validUntil().isAfter(settings.validFrom())) {
            throw ApiException.badRequest("validUntil must be after validFrom");
        }
        grant.setReleasePolicy(settings.releasePolicy());
        grant.setReleaseId(settings.releaseId());
        grant.setAllowClone(settings.allowClone());
        grant.setAnswerKeysVisible(settings.answerKeysVisible());
        grant.setValidFrom(settings.validFrom());
        grant.setValidUntil(settings.validUntil());
    }

    private PlatformBankGrant load(UUID grantId) {
        return grantRepository.findById(grantId).orElseThrow(() -> ApiException.notFound("Grant"));
    }

    private static Map<String, Object> settingsAudit(QuestionBank bank, PlatformBankGrant g) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("bankId", bank.getBankId());
        details.put("releasePolicy", g.getReleasePolicy());
        details.put("releaseId", g.getReleaseId());
        details.put("allowClone", g.isAllowClone());
        details.put("answerKeysVisible", g.isAnswerKeysVisible());
        return details;
    }
}
