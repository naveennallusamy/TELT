package com.telt.service;

import com.telt.access.QuestionAccess;
import com.telt.constants.*;
import com.telt.dto.common.PageResponse;
import com.telt.dto.question.*;
import com.telt.entity.question.*;
import com.telt.error.ApiException;
import com.telt.error.ErrorCode;
import com.telt.repository.*;
import com.telt.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class QuestionService {

    private static final int MAX_PAGE_SIZE = 100;

    private final QuestionBankService bankService;
    private final QuestionBankRepository bankRepository;
    private final QuestionRepository questionRepository;
    private final QuestionVersionRepository versionRepository;
    private final QuestionReviewRepository reviewRepository;
    private final TenantRepository tenantRepository;
    private final QuestionAccess access;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public QuestionResponse create(UUID bankId, CreateQuestionRequest request) {
        UUID tenantId = access.ownerTenant();
        QuestionBank bank = bankService.loadActive(tenantId, bankId);
        access.require(Permission.QUESTION_WRITE, bank);
        if (request.categoryId() != null) {
            bankService.category(tenantId, request.categoryId());
        }
        Question question = new Question();
        question.setTenantId(tenantId);
        question.setBankId(bankId);
        question.setCategoryId(request.categoryId());
        question.setType(request.type());
        question.setLatestVersion(1);
        questionRepository.save(question);
        QuestionVersion version = newVersion(question, 1, request.version());
        auditService.record(tenantId, "QUESTION_CREATED", "QUESTION", question.getQuestionId(),
                Map.of("bankId", bankId, "type", request.type()));
        return QuestionResponse.from(question, List.of(QuestionVersionResponse.from(version, true)));
    }

    @Transactional(readOnly = true)
    public QuestionResponse get(UUID questionId) {
        Loaded loaded = load(questionId, Permission.QUESTION_VIEW);
        boolean keys = access.canSeeAnswerKeys(loaded.bank());
        return QuestionResponse.from(loaded.question(), versionRepository
                .findByTenantIdAndQuestionIdOrderByVersionNo(loaded.question().getTenantId(), questionId).stream()
                .map(v -> QuestionVersionResponse.from(v, keys)).toList());
    }

    /**
     * New draft from the given content; only one open (draft or in-review) version is allowed at a time.
     */
    @Transactional
    public QuestionVersionResponse newDraft(UUID questionId, QuestionVersionRequest request) {
        Loaded loaded = load(questionId, Permission.QUESTION_WRITE);
        Question question = loaded.question();
        requireActive(question);
        QuestionVersion latest = version(question, question.getLatestVersion());
        if (latest.getStatus() == QuestionVersionStatus.DRAFT || latest.getStatus() == QuestionVersionStatus.IN_REVIEW) {
            throw ApiException.conflict("Version " + latest.getVersionNo() + " is still " + latest.getStatus());
        }
        question.setLatestVersion(question.getLatestVersion() + 1);
        QuestionVersion version = newVersion(question, question.getLatestVersion(), request);
        auditService.record(question.getTenantId(), "QUESTION_VERSION_CREATED", "QUESTION", questionId,
                Map.of("versionNo", version.getVersionNo()));
        return QuestionVersionResponse.from(version, true);
    }

    @Transactional
    public QuestionVersionResponse updateDraft(UUID questionId, int versionNo, QuestionVersionRequest request) {
        Loaded loaded = load(questionId, Permission.QUESTION_WRITE);
        QuestionVersion version = version(loaded.question(), versionNo);
        if (version.getStatus() != QuestionVersionStatus.DRAFT) {
            throw ApiException.conflict("Only draft versions can be edited; create a new version instead");
        }
        apply(loaded.question().getType(), version, request);
        return QuestionVersionResponse.from(version, true);
    }

    @Transactional
    public QuestionVersionResponse submit(UUID questionId, int versionNo) {
        Loaded loaded = load(questionId, Permission.QUESTION_WRITE);
        requireActive(loaded.question());
        QuestionVersion version = version(loaded.question(), versionNo);
        if (version.getStatus() != QuestionVersionStatus.DRAFT) {
            throw ApiException.conflict("Only draft versions can be submitted for review");
        }
        version.setStatus(QuestionVersionStatus.IN_REVIEW);
        version.setSubmittedAt(Instant.now());
        auditService.record(loaded.question().getTenantId(), "QUESTION_SUBMITTED", "QUESTION", questionId,
                Map.of("versionNo", versionNo));
        return QuestionVersionResponse.from(version, true);
    }

    @Transactional
    public QuestionVersionResponse review(UUID questionId, int versionNo, ReviewRequest request) {
        Loaded loaded = load(questionId, Permission.QUESTION_REVIEW);
        Question question = loaded.question();
        requireActive(question);
        QuestionVersion version = version(question, versionNo);
        if (version.getStatus() != QuestionVersionStatus.IN_REVIEW) {
            throw ApiException.conflict("Only versions in review can be reviewed");
        }
        UUID reviewer = TenantContext.requireCurrentUserId();
        boolean separation = tenantRepository.findById(question.getTenantId()).map(t -> t.isSeparationOfDuties())
                .orElse(true);
        if (separation && reviewer.equals(version.getAuthoredBy())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Authors cannot review their own question (separation of duties)");
        }
        Instant now = Instant.now();
        boolean approve = request.decision() == ReviewDecision.APPROVE;
        version.setStatus(approve ? QuestionVersionStatus.APPROVED : QuestionVersionStatus.REJECTED);
        version.setReviewedBy(reviewer);
        version.setReviewedAt(now);
        if (approve) {
            question.setApprovedVersion(versionNo);
        }
        QuestionReview review = new QuestionReview();
        review.setTenantId(question.getTenantId());
        review.setQuestionId(questionId);
        review.setVersionNo(versionNo);
        review.setReviewerId(reviewer);
        review.setDecision(request.decision());
        review.setComments(request.comments());
        review.setReviewedAt(now);
        reviewRepository.save(review);
        auditService.record(question.getTenantId(), approve ? "QUESTION_APPROVED" : "QUESTION_REJECTED", "QUESTION",
                questionId, Map.of("versionNo", versionNo));
        return QuestionVersionResponse.from(version, true);
    }

    /**
     * Retires the question: it can no longer be added to exams. Exams already published keep their snapshot.
     */
    @Transactional
    public QuestionResponse retire(UUID questionId) {
        Loaded loaded = load(questionId, Permission.QUESTION_PUBLISH);
        Question question = loaded.question();
        if (question.getStatus() == QuestionStatus.RETIRED) {
            return get(questionId);
        }
        question.setStatus(QuestionStatus.RETIRED);
        versionRepository.findByTenantIdAndQuestionIdOrderByVersionNo(question.getTenantId(), questionId).stream()
                .filter(v -> v.getStatus() == QuestionVersionStatus.APPROVED)
                .forEach(v -> v.setStatus(QuestionVersionStatus.RETIRED));
        question.setApprovedVersion(null);
        auditService.record(question.getTenantId(), "QUESTION_RETIRED", "QUESTION", questionId, Map.of());
        return get(questionId);
    }

    @Transactional(readOnly = true)
    public PageResponse<QuestionSummary> search(QuestionSearch search) {
        UUID tenantId = access.ownerTenant();
        int size = Math.clamp(search.size(), 1, MAX_PAGE_SIZE);
        int page = Math.max(search.page(), 0);
        List<UUID> bankIds = access.visible(bankRepository.findByTenantIdOrderByName(tenantId)).stream()
                .map(QuestionBank::getBankId)
                .filter(id -> search.bankId() == null || id.equals(search.bankId()))
                .toList();
        if (bankIds.isEmpty()) {
            return new PageResponse<>(List.of(), page, size, 0);
        }
        StringBuilder sql = new StringBuilder("""
                SELECT q.question_id, q.bank_id, q.category_id, q.question_type, q.status, q.latest_version,
                       v.status AS version_status, q.approved_version, v.content ->> 'text' AS text, v.difficulty,
                       v.default_marks, count(*) OVER () AS total
                FROM questions q
                JOIN question_versions v ON v.tenant_id = q.tenant_id AND v.question_id = q.question_id
                                        AND v.version_no = q.latest_version
                WHERE q.tenant_id = ? AND q.bank_id IN (""");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        sql.append(String.join(", ", Collections.nCopies(bankIds.size(), "?"))).append(')');
        args.addAll(bankIds);
        if (search.categoryId() != null) {
            String path = bankService.category(tenantId, search.categoryId()).getPath();
            sql.append(" AND q.category_id IN (SELECT category_id FROM question_categories WHERE tenant_id = ? AND path LIKE ?)");
            args.add(tenantId);
            args.add(path + "%");
        }
        if (search.type() != null) {
            sql.append(" AND q.question_type = ?");
            args.add(search.type().name());
        }
        if (search.status() != null) {
            sql.append(" AND v.status = ?");
            args.add(search.status().name());
        }
        if (search.difficulty() != null) {
            sql.append(" AND v.difficulty = ?");
            args.add(search.difficulty().name());
        }
        if (search.tag() != null && !search.tag().isBlank()) {
            sql.append(" AND v.tags @> jsonb_build_array(?::text)");
            args.add(search.tag());
        }
        if (search.q() != null && !search.q().isBlank()) {
            sql.append(" AND v.search_vector @@ plainto_tsquery('simple', ?)");
            args.add(search.q());
        }
        sql.append(" ORDER BY q.created_on DESC, q.question_id LIMIT ? OFFSET ?");
        args.add(size);
        args.add((long) page * size);
        long[] total = {0};
        List<QuestionSummary> items = jdbcTemplate.query(sql.toString(), (rs, i) -> {
            total[0] = rs.getLong("total");
            String difficulty = rs.getString("difficulty");
            int approved = rs.getInt("approved_version");
            return new QuestionSummary(rs.getObject("question_id", UUID.class), rs.getObject("bank_id", UUID.class),
                    rs.getObject("category_id", UUID.class), QuestionType.valueOf(rs.getString("question_type")),
                    QuestionStatus.valueOf(rs.getString("status")), rs.getInt("latest_version"),
                    QuestionVersionStatus.valueOf(rs.getString("version_status")), rs.wasNull() ? null : approved,
                    rs.getString("text"), difficulty == null ? null : Difficulty.valueOf(difficulty),
                    rs.getBigDecimal("default_marks"));
        }, args.toArray());
        return new PageResponse<>(items, page, size, total[0]);
    }

    private QuestionVersion newVersion(Question question, int versionNo, QuestionVersionRequest request) {
        QuestionVersion version = new QuestionVersion();
        version.setTenantId(question.getTenantId());
        version.setQuestionId(question.getQuestionId());
        version.setVersionNo(versionNo);
        version.setAuthoredBy(TenantContext.requireCurrentUserId());
        version.setCreatedOn(Instant.now());
        apply(question.getType(), version, request);
        return versionRepository.save(version);
    }

    private void apply(QuestionType type, QuestionVersion version, QuestionVersionRequest request) {
        validate(type, request.content(), request.answerKey());
        version.setContent(request.content());
        version.setAnswerKey(request.answerKey());
        version.setExplanation(request.explanation());
        version.setDefaultMarks(request.defaultMarks());
        version.setNegativeMarks(request.negativeMarks() == null ? BigDecimal.ZERO : request.negativeMarks());
        version.setDifficulty(request.difficulty());
        version.setTags(request.tags() == null ? new ArrayList<>() : new ArrayList<>(new LinkedHashSet<>(request.tags())));
    }

    static void validate(QuestionType type, QuestionContent content, AnswerKey key) {
        List<QuestionOption> options = content.optionsOrEmpty();
        Set<String> ids = new HashSet<>();
        options.forEach(o -> {
            if (!ids.add(o.id())) {
                throw ApiException.badRequest("Duplicate option id: " + o.id());
            }
        });
        switch (type) {
            case SINGLE_CHOICE, MULTIPLE_SELECT -> {
                if (options.size() < 2) {
                    throw ApiException.badRequest(type + " questions need at least two options");
                }
                List<String> correct = key == null ? List.of() : key.correctOrEmpty();
                if (correct.isEmpty() || !ids.containsAll(correct) || new HashSet<>(correct).size() != correct.size()) {
                    throw ApiException.badRequest("answerKey.correctOptionIds must reference distinct option ids");
                }
                if (type == QuestionType.SINGLE_CHOICE && correct.size() != 1) {
                    throw ApiException.badRequest("SINGLE_CHOICE questions need exactly one correct option");
                }
                if (key.value() != null || key.tolerance() != null) {
                    throw ApiException.badRequest("Choice questions do not take a numerical answer");
                }
            }
            case NUMERICAL -> {
                if (!options.isEmpty()) {
                    throw ApiException.badRequest("NUMERICAL questions do not have options");
                }
                if (key == null || key.value() == null || !key.correctOrEmpty().isEmpty()) {
                    throw ApiException.badRequest("NUMERICAL questions need answerKey.value");
                }
                if (key.tolerance() != null && key.tolerance().signum() < 0) {
                    throw ApiException.badRequest("answerKey.tolerance must not be negative");
                }
            }
            case DESCRIPTIVE -> {
                if (!options.isEmpty()) {
                    throw ApiException.badRequest("DESCRIPTIVE questions do not have options");
                }
                if (key != null && (key.value() != null || !key.correctOrEmpty().isEmpty())) {
                    throw ApiException.badRequest("DESCRIPTIVE answer keys only take a rubric");
                }
            }
        }
    }

    private record Loaded(Question question, QuestionBank bank) {
    }

    private Loaded load(UUID questionId, Permission permission) {
        UUID tenantId = access.ownerTenant();
        Question question = questionRepository.findByQuestionIdAndTenantId(questionId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Question"));
        QuestionBank bank = bankService.load(tenantId, question.getBankId());
        if (!access.can(Permission.QUESTION_VIEW, bank) && !access.can(permission, bank)) {
            throw ApiException.notFound("Question");
        }
        access.require(permission, bank);
        return new Loaded(question, bank);
    }

    private QuestionVersion version(Question question, int versionNo) {
        return versionRepository.findByTenantIdAndQuestionIdAndVersionNo(question.getTenantId(),
                question.getQuestionId(), versionNo).orElseThrow(() -> ApiException.notFound("Question version"));
    }

    private static void requireActive(Question question) {
        if (question.getStatus() != QuestionStatus.ACTIVE) {
            throw ApiException.conflict("Question is retired");
        }
    }
}
