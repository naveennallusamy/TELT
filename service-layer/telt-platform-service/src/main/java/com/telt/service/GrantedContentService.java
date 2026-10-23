package com.telt.service;

import com.telt.access.AuthorizationService;
import com.telt.access.QuestionAccess;
import com.telt.constants.GrantStatus;
import com.telt.constants.Permission;
import com.telt.constants.PlatformTenant;
import com.telt.dto.common.PageResponse;
import com.telt.dto.platform.CloneRequest;
import com.telt.dto.platform.GrantResponse;
import com.telt.dto.platform.GrantedQuestionResponse;
import com.telt.dto.question.QuestionResponse;
import com.telt.dto.question.QuestionVersionResponse;
import com.telt.entity.question.*;
import com.telt.error.ApiException;
import com.telt.error.ErrorCode;
import com.telt.repository.*;
import com.telt.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tenant side of platform banks: read-only access to the release a grant exposes, and cloning when allowed.
 * Reads run under the tenant's row-level security, which only exposes granted releases.
 */
@Service
@RequiredArgsConstructor
public class GrantedContentService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PlatformBankService platformBankService;
    private final QuestionBankService bankService;
    private final PlatformBankGrantRepository grantRepository;
    private final BankReleaseItemRepository itemRepository;
    private final QuestionRepository questionRepository;
    private final QuestionVersionRepository versionRepository;
    private final AuthorizationService authorizationService;
    private final QuestionAccess access;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<GrantResponse> grants() {
        UUID tenantId = TenantContext.requireCurrentTenant();
        Instant now = Instant.now();
        return grantRepository.findByTenantIdAndStatusOrderByCreatedOn(tenantId, GrantStatus.ACTIVE)
                .stream().filter(g -> g.isUsable(now))
                .map(g -> platformBankService.response(g, bankService.load(PlatformTenant.ID, g.getBankId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<GrantedQuestionResponse> questions(UUID grantId, int page, int size) {
        PlatformBankGrant grant = usableGrant(grantId);
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int pageNo = Math.max(page, 0);
        BankRelease release = platformBankService.effectiveRelease(grant).orElse(null);
        if (release == null) {
            return new PageResponse<>(List.of(), pageNo, pageSize, 0);
        }
        boolean keys = canSeeKeys(grant);
        Page<BankReleaseItem> items = itemRepository.findByReleaseIdOrderByQuestionId(release.getReleaseId(),
                PageRequest.of(pageNo, pageSize));
        return new PageResponse<>(items.stream().map(i -> view(i, keys)).toList(), pageNo, pageSize,
                items.getTotalElements());
    }

    @Transactional(readOnly = true)
    public GrantedQuestionResponse question(UUID grantId, UUID questionId) {
        PlatformBankGrant grant = usableGrant(grantId);
        return view(item(grant, questionId), canSeeKeys(grant));
    }

    /**
     * Copies the released version into a tenant bank as a new draft question that the tenant owns and can edit.
     */
    @Transactional
    public QuestionResponse cloneQuestion(UUID grantId, UUID questionId, CloneRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        PlatformBankGrant grant = usableGrant(grantId);
        if (!grant.isAllowClone()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "This grant does not allow cloning");
        }
        BankReleaseItem item = item(grant, questionId);
        QuestionBank bank = bankService.loadActive(tenantId, request.bankId());
        access.require(Permission.QUESTION_WRITE, bank);
        if (request.categoryId() != null) {
            bankService.category(tenantId, request.categoryId());
        }
        Question source = platformQuestion(questionId);
        QuestionVersion sourceVersion = platformVersion(item);

        Question copy = new Question();
        copy.setTenantId(tenantId);
        copy.setBankId(bank.getBankId());
        copy.setCategoryId(request.categoryId());
        copy.setType(source.getType());
        copy.setLatestVersion(1);
        copy.setSourceQuestionId(source.getQuestionId());
        copy.setSourceVersionNo(item.getVersionNo());
        copy.setSourceGrantId(grantId);
        questionRepository.save(copy);

        QuestionVersion version = new QuestionVersion();
        version.setTenantId(tenantId);
        version.setQuestionId(copy.getQuestionId());
        version.setVersionNo(1);
        version.setContent(sourceVersion.getContent());
        version.setAnswerKey(sourceVersion.getAnswerKey());
        version.setExplanation(sourceVersion.getExplanation());
        version.setDefaultMarks(sourceVersion.getDefaultMarks());
        version.setNegativeMarks(sourceVersion.getNegativeMarks());
        version.setDifficulty(sourceVersion.getDifficulty());
        version.setTags(new ArrayList<>(sourceVersion.getTags()));
        version.setAuthoredBy(TenantContext.requireCurrentUserId());
        version.setCreatedOn(Instant.now());
        versionRepository.save(version);
        auditService.record(tenantId, "QUESTION_CLONED", "QUESTION", copy.getQuestionId(),
                Map.of("sourceQuestionId", questionId, "sourceVersionNo", item.getVersionNo(), "grantId", grantId));
        return QuestionResponse.from(copy, List.of(QuestionVersionResponse.from(version, true)));
    }

    private PlatformBankGrant usableGrant(UUID grantId) {
        return grantRepository.findByGrantIdAndTenantId(grantId, TenantContext.requireCurrentTenant())
                .filter(g -> g.isUsable(Instant.now()))
                .orElseThrow(() -> ApiException.notFound("Grant"));
    }

    private BankReleaseItem item(PlatformBankGrant grant, UUID questionId) {
        return platformBankService.effectiveRelease(grant)
                .flatMap(r -> itemRepository.findByReleaseIdAndQuestionId(r.getReleaseId(), questionId))
                .orElseThrow(() -> ApiException.notFound("Question"));
    }

    private boolean canSeeKeys(PlatformBankGrant grant) {
        return grant.isAnswerKeysVisible() && authorizationService.current().has(Permission.ANSWER_KEY_VIEW);
    }

    private GrantedQuestionResponse view(BankReleaseItem item, boolean keys) {
        Question question = platformQuestion(item.getQuestionId());
        return new GrantedQuestionResponse(question.getQuestionId(), question.getCategoryId(), question.getType(),
                QuestionVersionResponse.from(platformVersion(item), keys));
    }

    private Question platformQuestion(UUID questionId) {
        return questionRepository.findByQuestionIdAndTenantId(questionId, PlatformTenant.ID)
                .orElseThrow(() -> ApiException.notFound("Question"));
    }

    private QuestionVersion platformVersion(BankReleaseItem item) {
        return versionRepository.findByTenantIdAndQuestionIdAndVersionNo(PlatformTenant.ID, item.getQuestionId(),
                item.getVersionNo()).orElseThrow(() -> ApiException.notFound("Question version"));
    }
}
