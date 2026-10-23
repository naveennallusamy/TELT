package com.telt.service;

import com.telt.access.AuthorizationService;
import com.telt.access.ExamAccess;
import com.telt.access.QuestionAccess;
import com.telt.constants.*;
import com.telt.dto.exam.*;
import com.telt.dto.question.ReviewRequest;
import com.telt.entity.exam.*;
import com.telt.entity.question.*;
import com.telt.error.ApiException;
import com.telt.error.ErrorCode;
import com.telt.repository.*;
import com.telt.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Exam authoring: versions with sections (segments) and items, review, and publishing, which snapshots every
 * question into exam-owned rows so later bank changes or grant revocation never affect the published exam.
 */
@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamRepository examRepository;
    private final ExamVersionRepository versionRepository;
    private final ExamSectionRepository sectionRepository;
    private final ExamItemRepository itemRepository;
    private final ExamQuestionSnapshotRepository snapshotRepository;
    private final ExamReviewRepository reviewRepository;
    private final DepartmentRepository departmentRepository;
    private final TenantRepository tenantRepository;
    private final QuestionRepository questionRepository;
    private final QuestionVersionRepository questionVersionRepository;
    private final PlatformBankGrantRepository grantRepository;
    private final BankReleaseItemRepository releaseItemRepository;
    private final QuestionBankService bankService;
    private final PlatformBankService platformBankService;
    private final QuestionAccess questionAccess;
    private final ExamAccess access;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<ExamResponse> list() {
        return access.visible(examRepository.findByTenantIdOrderByCode(questionAccess.ownerTenant())).stream()
                .map(ExamResponse::from).toList();
    }

    @Transactional
    public ExamDetailResponse create(CreateExamRequest request) {
        UUID tenantId = questionAccess.ownerTenant();
        Exam exam = new Exam();
        exam.setTenantId(tenantId);
        if (request.departmentId() != null) {
            exam.setDepartmentId(departmentRepository.findByDepartmentIdAndTenantId(request.departmentId(), tenantId)
                    .orElseThrow(() -> ApiException.notFound("Department")).getDepartmentId());
        }
        access.require(Permission.EXAM_CREATE, exam);
        if (examRepository.existsByTenantIdAndCodeIgnoreCase(tenantId, request.code())) {
            throw ApiException.conflict("Exam code already exists");
        }
        exam.setCode(request.code());
        exam.setTitle(request.title());
        exam.setDescription(request.description());
        exam.setLatestVersion(1);
        examRepository.save(exam);
        ExamVersion version = newVersion(exam, 1, request.version());
        auditService.record(tenantId, "EXAM_CREATED", "EXAM", exam.getExamId(), Map.of("code", request.code()));
        return new ExamDetailResponse(ExamResponse.from(exam), List.of(view(version)));
    }

    @Transactional(readOnly = true)
    public ExamDetailResponse get(UUID examId) {
        Exam exam = load(examId, null);
        return new ExamDetailResponse(ExamResponse.from(exam), versionRepository
                .findByTenantIdAndExamIdOrderByVersionNo(exam.getTenantId(), examId).stream().map(this::view).toList());
    }

    @Transactional
    public ExamVersionResponse newDraft(UUID examId, ExamVersionRequest request) {
        Exam exam = load(examId, Permission.EXAM_EDIT);
        requireAuthored(exam);
        requireActive(exam);
        ExamVersion latest = version(exam, exam.getLatestVersion());
        if (latest.getStatus() == ExamVersionStatus.DRAFT || latest.getStatus() == ExamVersionStatus.IN_REVIEW) {
            throw ApiException.conflict("Version " + latest.getVersionNo() + " is still " + latest.getStatus());
        }
        exam.setLatestVersion(exam.getLatestVersion() + 1);
        ExamVersion version = newVersion(exam, exam.getLatestVersion(), request);
        auditService.record(exam.getTenantId(), "EXAM_VERSION_CREATED", "EXAM", examId,
                Map.of("versionNo", version.getVersionNo()));
        return view(version);
    }

    @Transactional
    public ExamVersionResponse updateDraft(UUID examId, int versionNo, ExamVersionRequest request) {
        Exam exam = load(examId, Permission.EXAM_EDIT);
        requireAuthored(exam);
        ExamVersion version = version(exam, versionNo);
        if (version.getStatus() != ExamVersionStatus.DRAFT) {
            throw ApiException.conflict("Only draft versions can be edited; create a new version instead");
        }
        List<ExamSection> old = sectionRepository.findByTenantIdAndExamIdAndVersionNoOrderByPosition(exam.getTenantId(),
                examId, versionNo);
        itemRepository.deleteAll(itemRepository.findByTenantIdAndSectionIdInOrderByPosition(exam.getTenantId(),
                old.stream().map(ExamSection::getSectionId).toList()));
        sectionRepository.deleteAll(old);
        sectionRepository.flush();
        build(exam, version, request);
        return view(version);
    }

    @Transactional
    public ExamVersionResponse submit(UUID examId, int versionNo) {
        Exam exam = load(examId, Permission.EXAM_EDIT);
        requireAuthored(exam);
        requireActive(exam);
        ExamVersion version = version(exam, versionNo);
        if (version.getStatus() != ExamVersionStatus.DRAFT) {
            throw ApiException.conflict("Only draft versions can be submitted for review");
        }
        version.setStatus(ExamVersionStatus.IN_REVIEW);
        version.setSubmittedAt(Instant.now());
        auditService.record(exam.getTenantId(), "EXAM_SUBMITTED", "EXAM", examId, Map.of("versionNo", versionNo));
        return view(version);
    }

    /**
     * Approve, or reject back to draft for further edits.
     */
    @Transactional
    public ExamVersionResponse review(UUID examId, int versionNo, ReviewRequest request) {
        Exam exam = load(examId, Permission.EXAM_REVIEW);
        requireAuthored(exam);
        requireActive(exam);
        ExamVersion version = version(exam, versionNo);
        if (version.getStatus() != ExamVersionStatus.IN_REVIEW) {
            throw ApiException.conflict("Only versions in review can be reviewed");
        }
        UUID reviewer = TenantContext.requireCurrentUserId();
        boolean separation = tenantRepository.findById(exam.getTenantId()).map(t -> t.isSeparationOfDuties())
                .orElse(true);
        if (separation && reviewer.equals(version.getAuthoredBy())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Authors cannot review their own exam (separation of duties)");
        }
        boolean approve = request.decision() == ReviewDecision.APPROVE;
        Instant now = Instant.now();
        version.setStatus(approve ? ExamVersionStatus.APPROVED : ExamVersionStatus.DRAFT);
        version.setReviewedBy(reviewer);
        version.setReviewedAt(now);
        ExamReview review = new ExamReview();
        review.setTenantId(exam.getTenantId());
        review.setExamId(examId);
        review.setVersionNo(versionNo);
        review.setReviewerId(reviewer);
        review.setDecision(request.decision());
        review.setComments(request.comments());
        review.setReviewedAt(now);
        reviewRepository.save(review);
        auditService.record(exam.getTenantId(), approve ? "EXAM_APPROVED" : "EXAM_REJECTED", "EXAM", examId,
                Map.of("versionNo", versionNo));
        return view(version);
    }

    /**
     * Re-checks every item and copies the questions into exam-owned snapshot rows; the previous published
     * version is retired (schedules created from it keep their own version).
     */
    @Transactional
    public ExamVersionResponse publish(UUID examId, int versionNo) {
        Exam exam = load(examId, Permission.EXAM_PUBLISH);
        requireAuthored(exam);
        requireActive(exam);
        ExamVersion version = version(exam, versionNo);
        if (version.getStatus() != ExamVersionStatus.APPROVED) {
            throw ApiException.conflict("Only approved versions can be published");
        }
        List<ExamSection> sections = sections(exam, versionNo);
        Map<UUID, List<ExamItem>> items = items(exam, sections);
        List<ExamQuestionSnapshot> snapshots = new ArrayList<>();
        for (ExamSection section : sections) {
            for (ExamItem item : items.getOrDefault(section.getSectionId(), List.of())) {
                Source source = item.getGrantId() == null
                        ? own(exam, item.getQuestionId(), item.getQuestionVersionNo())
                        : granted(exam, item.getGrantId(), item.getQuestionId(), item.getQuestionVersionNo());
                QuestionVersion qv = source.version();
                ExamQuestionSnapshot s = new ExamQuestionSnapshot();
                s.setTenantId(exam.getTenantId());
                s.setExamId(examId);
                s.setVersionNo(versionNo);
                s.setSectionId(section.getSectionId());
                s.setItemId(item.getItemId());
                s.setPosition(item.getPosition());
                s.setQuestionType(source.question().getType());
                s.setContent(qv.getContent());
                s.setAnswerKey(qv.getAnswerKey());
                s.setExplanation(qv.getExplanation());
                s.setAnswerKeyVisible(source.grant() == null || source.grant().isAnswerKeysVisible());
                s.setMarks(item.getMarks() != null ? item.getMarks() : qv.getDefaultMarks());
                s.setNegativeMarks(!Boolean.TRUE.equals(version.getSettings().negativeMarking()) ? BigDecimal.ZERO
                        : item.getNegativeMarks() != null ? item.getNegativeMarks() : qv.getNegativeMarks());
                s.setSourceTenantId(item.getSourceTenantId());
                s.setSourceQuestionId(item.getQuestionId());
                s.setSourceVersionNo(item.getQuestionVersionNo());
                snapshots.add(s);
            }
        }
        snapshotRepository.saveAll(snapshots);
        if (exam.getPublishedVersion() != null) {
            version(exam, exam.getPublishedVersion()).setStatus(ExamVersionStatus.RETIRED);
        }
        Instant now = Instant.now();
        version.setStatus(ExamVersionStatus.PUBLISHED);
        version.setPublishedBy(TenantContext.requireCurrentUserId());
        version.setPublishedAt(now);
        exam.setPublishedVersion(versionNo);
        auditService.record(exam.getTenantId(), "EXAM_PUBLISHED", "EXAM", examId,
                Map.of("versionNo", versionNo, "questions", snapshots.size()));
        return view(version);
    }

    @Transactional(readOnly = true)
    public List<SnapshotResponse> snapshot(UUID examId, int versionNo) {
        Exam exam = load(examId, null);
        boolean keys = access.can(Permission.ANSWER_KEY_VIEW, exam) || access.can(Permission.EXAM_EDIT, exam);
        return snapshotRepository.findByTenantIdAndExamIdAndVersionNoOrderByPosition(exam.getTenantId(), examId,
                versionNo).stream().map(s -> SnapshotResponse.from(s, keys)).toList();
    }

    @Transactional
    public ExamResponse archive(UUID examId) {
        Exam exam = load(examId, Permission.EXAM_PUBLISH);
        if (exam.getStatus() != RecordStatus.INACTIVE) {
            exam.setStatus(RecordStatus.INACTIVE);
            auditService.record(exam.getTenantId(), "EXAM_ARCHIVED", "EXAM", examId, Map.of());
        }
        return ExamResponse.from(exam);
    }

    private ExamVersion newVersion(Exam exam, int versionNo, ExamVersionRequest request) {
        ExamVersion version = new ExamVersion();
        version.setTenantId(exam.getTenantId());
        version.setExamId(exam.getExamId());
        version.setVersionNo(versionNo);
        version.setAuthoredBy(TenantContext.requireCurrentUserId());
        version.setCreatedOn(Instant.now());
        version.setSettings(request.settings().normalized());
        versionRepository.saveAndFlush(version);
        build(exam, version, request);
        return version;
    }

    /**
     * Validates and stores sections and items, freezing each item to the question version it resolves to now.
     */
    private void build(Exam exam, ExamVersion version, ExamVersionRequest request) {
        ExamSettings settings = request.settings().normalized();
        int timed = 0;
        BigDecimal total = BigDecimal.ZERO;
        Set<UUID> seen = new HashSet<>();
        int sectionNo = 0;
        for (SectionRequest sr : request.sections()) {
            if (sr.timeLimitMinutes() != null) {
                timed += sr.timeLimitMinutes();
            }
            ExamSection section = new ExamSection();
            section.setTenantId(exam.getTenantId());
            section.setExamId(exam.getExamId());
            section.setVersionNo(version.getVersionNo());
            section.setPosition(++sectionNo);
            section.setTitle(sr.title());
            section.setTimeLimitMinutes(sr.timeLimitMinutes());
            sectionRepository.saveAndFlush(section);
            List<BigDecimal> marks = new ArrayList<>();
            int itemNo = 0;
            for (ItemRequest ir : sr.items()) {
                if (!seen.add(ir.questionId())) {
                    throw ApiException.badRequest("Question " + ir.questionId() + " appears more than once");
                }
                Source source = ir.grantId() == null ? own(exam, ir.questionId(), null)
                        : granted(exam, ir.grantId(), ir.questionId(), null);
                ExamItem item = new ExamItem();
                item.setTenantId(exam.getTenantId());
                item.setSectionId(section.getSectionId());
                item.setPosition(++itemNo);
                item.setSourceTenantId(source.question().getTenantId());
                item.setQuestionId(ir.questionId());
                item.setQuestionVersionNo(source.version().getVersionNo());
                item.setGrantId(ir.grantId());
                item.setMarks(ir.marks());
                item.setNegativeMarks(ir.negativeMarks());
                itemRepository.save(item);
                marks.add(ir.marks() != null ? ir.marks() : source.version().getDefaultMarks());
            }
            total = total.add(marks.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        if (timed > settings.durationMinutes()) {
            throw ApiException.badRequest("Section time limits exceed the exam duration");
        }
        if (settings.passMarks() != null && settings.passMarks().compareTo(total) > 0) {
            throw ApiException.badRequest("passMarks exceeds the exam's total marks");
        }
        version.setTotalMarks(total);
    }

    private record Source(Question question, QuestionVersion version, PlatformBankGrant grant) {
    }

    /**
     * The owner's own question: its approved version (or the pinned one when re-checking at publish).
     */
    private Source own(Exam exam, UUID questionId, Integer pinned) {
        Question question = questionRepository.findByQuestionIdAndTenantId(questionId, exam.getTenantId())
                .orElseThrow(() -> ApiException.badRequest("Question " + questionId + " not found"));
        QuestionBank bank = bankService.load(exam.getTenantId(), question.getBankId());
        if (!questionAccess.can(Permission.QUESTION_VIEW, bank)) {
            throw ApiException.badRequest("Question " + questionId + " not found");
        }
        Integer versionNo = pinned != null ? pinned : question.getApprovedVersion();
        if (question.getStatus() != QuestionStatus.ACTIVE || versionNo == null) {
            throw ApiException.badRequest("Question " + questionId + " has no approved version");
        }
        QuestionVersion version = questionVersionRepository.findByTenantIdAndQuestionIdAndVersionNo(exam.getTenantId(),
                questionId, versionNo).filter(v -> v.getStatus() == QuestionVersionStatus.APPROVED)
                .orElseThrow(() -> ApiException.badRequest("Question " + questionId + " version " + versionNo
                        + " is no longer approved"));
        return new Source(question, version, null);
    }

    /**
     * A platform question through the tenant's grant: the version in the grant's current release.
     */
    private Source granted(Exam exam, UUID grantId, UUID questionId, Integer pinned) {
        if (PlatformTenant.is(exam.getTenantId())) {
            throw ApiException.badRequest("Platform exams use platform questions directly, without a grant");
        }
        PlatformBankGrant grant = grantRepository.findByGrantIdAndTenantId(grantId, exam.getTenantId())
                .filter(g -> g.isUsable(Instant.now()))
                .orElseThrow(() -> ApiException.badRequest("Grant " + grantId + " is not active"));
        BankReleaseItem item = platformBankService.effectiveRelease(grant)
                .flatMap(r -> releaseItemRepository.findByReleaseIdAndQuestionId(r.getReleaseId(), questionId))
                .filter(i -> pinned == null || i.getVersionNo() == pinned)
                .orElseThrow(() -> ApiException.badRequest("Question " + questionId
                        + " is not in the grant's current release"));
        Question question = questionRepository.findByQuestionIdAndTenantId(questionId, PlatformTenant.ID)
                .orElseThrow(() -> ApiException.badRequest("Question " + questionId + " not found"));
        QuestionVersion version = questionVersionRepository.findByTenantIdAndQuestionIdAndVersionNo(PlatformTenant.ID,
                questionId, item.getVersionNo()).orElseThrow(() -> ApiException.notFound("Question version"));
        return new Source(question, version, grant);
    }

    private Exam load(UUID examId, Permission permission) {
        Exam exam = examRepository.findByExamIdAndTenantId(examId, questionAccess.ownerTenant())
                .orElseThrow(() -> ApiException.notFound("Exam"));
        if (!access.canView(exam) && (permission == null || !access.can(permission, exam))) {
            throw ApiException.notFound("Exam");
        }
        if (permission != null) {
            access.require(permission, exam);
        }
        return exam;
    }

    private ExamVersion version(Exam exam, int versionNo) {
        return versionRepository.findByTenantIdAndExamIdAndVersionNo(exam.getTenantId(), exam.getExamId(), versionNo)
                .orElseThrow(() -> ApiException.notFound("Exam version"));
    }

    private List<ExamSection> sections(Exam exam, int versionNo) {
        return sectionRepository.findByTenantIdAndExamIdAndVersionNoOrderByPosition(exam.getTenantId(),
                exam.getExamId(), versionNo);
    }

    private Map<UUID, List<ExamItem>> items(Exam exam, List<ExamSection> sections) {
        Map<UUID, List<ExamItem>> bySection = new LinkedHashMap<>();
        itemRepository.findByTenantIdAndSectionIdInOrderByPosition(exam.getTenantId(),
                        sections.stream().map(ExamSection::getSectionId).toList())
                .forEach(i -> bySection.computeIfAbsent(i.getSectionId(), k -> new ArrayList<>()).add(i));
        return bySection;
    }

    private ExamVersionResponse view(ExamVersion version) {
        Exam exam = examRepository.findByExamIdAndTenantId(version.getExamId(), version.getTenantId()).orElseThrow();
        List<ExamSection> sections = sections(exam, version.getVersionNo());
        Map<UUID, List<ExamItem>> items = items(exam, sections);
        return ExamVersionResponse.from(version, sections.stream().map(s -> new SectionResponse(s.getSectionId(),
                s.getPosition(), s.getTitle(), s.getTimeLimitMinutes(),
                items.getOrDefault(s.getSectionId(), List.of()).stream().map(ItemResponse::from).toList())).toList());
    }

    private static void requireAuthored(Exam exam) {
        if (exam.getSourceExamId() != null) {
            throw ApiException.conflict("Distributed platform exams are read-only");
        }
    }

    private static void requireActive(Exam exam) {
        if (exam.getStatus() != RecordStatus.ACTIVE) {
            throw ApiException.conflict("Exam is archived");
        }
    }
}
