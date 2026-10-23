package com.telt.service;

import com.telt.constants.*;
import com.telt.dto.exam.ExamResponse;
import com.telt.dto.schedule.DistributeExamRequest;
import com.telt.dto.schedule.DistributionResponse;
import com.telt.dto.schedule.ScheduleResponse;
import com.telt.entity.exam.*;
import com.telt.error.ApiException;
import com.telt.repository.*;
import com.telt.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Super-admin side of exams: distributing platform exams to tenants as read-only copies, and reading any tenant's
 * exams and schedules. Every cross-tenant read is written to that tenant's audit log.
 */
@Service
@RequiredArgsConstructor
public class PlatformExamService {

    private final ExamRepository examRepository;
    private final ExamVersionRepository versionRepository;
    private final ExamSectionRepository sectionRepository;
    private final ExamQuestionSnapshotRepository snapshotRepository;
    private final ExamDistributionRepository distributionRepository;
    private final ExamScheduleRepository scheduleRepository;
    private final TenantRepository tenantRepository;
    private final ExamScheduleService scheduleService;
    private final AuditService auditService;

    /**
     * Copies the platform exam's published version (sections and question snapshots) into the tenant. Sending again
     * after a new version is published adds that version to the copy; existing schedules keep their version.
     */
    @Transactional
    public DistributionResponse distribute(UUID examId, DistributeExamRequest request) {
        Exam source = platformExam(examId);
        if (source.getStatus() != RecordStatus.ACTIVE) {
            throw ApiException.conflict("Exam is archived");
        }
        if (source.getPublishedVersion() == null) {
            throw ApiException.conflict("Publish the exam before distributing it");
        }
        UUID tenantId = request.tenantId();
        requireTenant(tenantId);
        int versionNo = source.getPublishedVersion();
        boolean keys = Boolean.TRUE.equals(request.answerKeysVisible());
        ExamDistribution distribution = distributionRepository.findByTenantIdAndSourceExamId(tenantId, examId)
                .orElse(null);
        Exam copy;
        if (distribution == null) {
            if (examRepository.existsByTenantIdAndCodeIgnoreCase(tenantId, source.getCode())) {
                throw ApiException.conflict("The tenant already has an exam with code " + source.getCode());
            }
            copy = new Exam();
            copy.setTenantId(tenantId);
            copy.setCode(source.getCode());
            copy.setTitle(source.getTitle());
            copy.setDescription(source.getDescription());
            copy.setSourceExamId(examId);
            examRepository.saveAndFlush(copy);
            distribution = new ExamDistribution();
            distribution.setTenantId(tenantId);
            distribution.setSourceExamId(examId);
            distribution.setExamId(copy.getExamId());
        } else {
            copy = examRepository.findByExamIdAndTenantId(distribution.getExamId(), tenantId).orElseThrow();
            if (distribution.getStatus() == GrantStatus.ACTIVE && distribution.getSourceVersionNo() == versionNo) {
                throw ApiException.conflict("The tenant already has version " + versionNo
                        + "; update the distribution to change its policy");
            }
        }
        if (versionRepository.findByTenantIdAndExamIdAndVersionNo(tenantId, copy.getExamId(), versionNo).isEmpty()) {
            copyVersion(source, versionNo, copy, keys);
        }
        copy.setStatus(RecordStatus.ACTIVE);
        distribution.setSourceVersionNo(versionNo);
        distribution.setOverridePolicy(policy(request.overridePolicy()));
        distribution.setAnswerKeysVisible(keys);
        distribution.setStatus(GrantStatus.ACTIVE);
        distribution.setRevokedAt(null);
        distribution.setDistributedBy(TenantContext.requireCurrentUserId());
        distribution.setDistributedAt(Instant.now());
        distributionRepository.save(distribution);
        auditService.record(tenantId, "EXAM_DISTRIBUTED", "EXAM", copy.getExamId(),
                Map.of("sourceExamId", examId, "versionNo", versionNo));
        return response(distribution);
    }

    @Transactional(readOnly = true)
    public List<DistributionResponse> distributions(UUID examId) {
        platformExam(examId);
        return distributionRepository.findBySourceExamIdOrderByDistributedAt(examId).stream().map(this::response).toList();
    }

    @Transactional
    public DistributionResponse updatePolicy(UUID distributionId, OverridePolicy policy) {
        ExamDistribution distribution = distribution(distributionId);
        if (distribution.getStatus() != GrantStatus.ACTIVE) {
            throw ApiException.conflict("Revoked distributions cannot be changed");
        }
        distribution.setOverridePolicy(policy(policy));
        auditService.record(distribution.getTenantId(), "EXAM_DISTRIBUTION_UPDATED", "EXAM", distribution.getExamId(),
                Map.of("overridePolicy", distribution.getOverridePolicy().toString()));
        return response(distribution);
    }

    /**
     * Archives the tenant's copy and cancels its schedules that have not started; running ones finish normally.
     */
    @Transactional
    public DistributionResponse revoke(UUID distributionId) {
        ExamDistribution distribution = distribution(distributionId);
        if (distribution.getStatus() == GrantStatus.ACTIVE) {
            Instant now = Instant.now();
            distribution.setStatus(GrantStatus.REVOKED);
            distribution.setRevokedAt(now);
            examRepository.findByExamIdAndTenantId(distribution.getExamId(), distribution.getTenantId()).orElseThrow()
                    .setStatus(RecordStatus.INACTIVE);
            int cancelled = 0;
            for (ExamSchedule s : scheduleRepository.findByTenantIdAndExamIdOrderByStartsAt(distribution.getTenantId(),
                    distribution.getExamId())) {
                if (s.getStatus() == ScheduleStatus.SCHEDULED && s.getStartsAt().isAfter(now)) {
                    s.setStatus(ScheduleStatus.CANCELLED);
                    cancelled++;
                }
            }
            auditService.record(distribution.getTenantId(), "EXAM_DISTRIBUTION_REVOKED", "EXAM",
                    distribution.getExamId(), Map.of("cancelledSchedules", cancelled));
        }
        return response(distribution);
    }

    @Transactional
    public List<ExamResponse> tenantExams(UUID tenantId) {
        requireTenant(tenantId);
        auditService.record(tenantId, "PLATFORM_TENANT_VIEWED", "TENANT", tenantId, Map.of("view", "exams"));
        return examRepository.findByTenantIdOrderByCode(tenantId).stream().map(ExamResponse::from).toList();
    }

    @Transactional
    public List<ScheduleResponse> tenantSchedules(UUID tenantId) {
        requireTenant(tenantId);
        auditService.record(tenantId, "PLATFORM_TENANT_VIEWED", "TENANT", tenantId, Map.of("view", "exam-schedules"));
        return scheduleRepository.findByTenantIdOrderByStartsAt(tenantId).stream().map(scheduleService::view).toList();
    }

    private void copyVersion(Exam source, int versionNo, Exam copy, boolean answerKeysVisible) {
        UUID tenantId = copy.getTenantId();
        ExamVersion from = versionRepository.findByTenantIdAndExamIdAndVersionNo(PlatformTenant.ID,
                source.getExamId(), versionNo).orElseThrow();
        ExamVersion version = new ExamVersion();
        version.setTenantId(tenantId);
        version.setExamId(copy.getExamId());
        version.setVersionNo(versionNo);
        version.setSettings(from.getSettings());
        version.setTotalMarks(from.getTotalMarks());
        version.setAuthoredBy(from.getAuthoredBy());
        version.setCreatedOn(Instant.now());
        versionRepository.saveAndFlush(version);
        Map<UUID, UUID> sectionIds = new HashMap<>();
        for (ExamSection s : sectionRepository.findByTenantIdAndExamIdAndVersionNoOrderByPosition(PlatformTenant.ID,
                source.getExamId(), versionNo)) {
            ExamSection section = new ExamSection();
            section.setTenantId(tenantId);
            section.setExamId(copy.getExamId());
            section.setVersionNo(versionNo);
            section.setPosition(s.getPosition());
            section.setTitle(s.getTitle());
            section.setTimeLimitMinutes(s.getTimeLimitMinutes());
            sectionRepository.saveAndFlush(section);
            sectionIds.put(s.getSectionId(), section.getSectionId());
        }
        List<ExamQuestionSnapshot> snapshots = new ArrayList<>();
        for (ExamQuestionSnapshot s : snapshotRepository.findByTenantIdAndExamIdAndVersionNoOrderByPosition(
                PlatformTenant.ID, source.getExamId(), versionNo)) {
            ExamQuestionSnapshot snapshot = new ExamQuestionSnapshot();
            snapshot.setTenantId(tenantId);
            snapshot.setExamId(copy.getExamId());
            snapshot.setVersionNo(versionNo);
            snapshot.setSectionId(sectionIds.get(s.getSectionId()));
            snapshot.setItemId(s.getItemId());
            snapshot.setPosition(s.getPosition());
            snapshot.setQuestionType(s.getQuestionType());
            snapshot.setContent(s.getContent());
            snapshot.setAnswerKey(s.getAnswerKey());
            snapshot.setExplanation(s.getExplanation());
            snapshot.setAnswerKeyVisible(answerKeysVisible);
            snapshot.setMarks(s.getMarks());
            snapshot.setNegativeMarks(s.getNegativeMarks());
            snapshot.setSourceTenantId(s.getSourceTenantId());
            snapshot.setSourceQuestionId(s.getSourceQuestionId());
            snapshot.setSourceVersionNo(s.getSourceVersionNo());
            snapshots.add(snapshot);
        }
        snapshotRepository.saveAllAndFlush(snapshots);
        if (copy.getPublishedVersion() != null) {
            versionRepository.findByTenantIdAndExamIdAndVersionNo(tenantId, copy.getExamId(), copy.getPublishedVersion())
                    .ifPresent(v -> v.setStatus(ExamVersionStatus.RETIRED));
        }
        version.setStatus(ExamVersionStatus.PUBLISHED);
        version.setPublishedBy(TenantContext.requireCurrentUserId());
        version.setPublishedAt(Instant.now());
        versionRepository.flush();
        copy.setLatestVersion(Math.max(copy.getLatestVersion(), versionNo));
        copy.setPublishedVersion(versionNo);
    }

    private static OverridePolicy policy(OverridePolicy policy) {
        return (policy == null ? new OverridePolicy(null, null, null, null, null, null) : policy).normalized();
    }

    private DistributionResponse response(ExamDistribution d) {
        return DistributionResponse.from(d, scheduleRepository.countByTenantIdAndExamIdAndStatus(d.getTenantId(),
                d.getExamId(), ScheduleStatus.SCHEDULED));
    }

    private Exam platformExam(UUID examId) {
        return examRepository.findByExamIdAndTenantId(examId, PlatformTenant.ID)
                .orElseThrow(() -> ApiException.notFound("Exam"));
    }

    private ExamDistribution distribution(UUID distributionId) {
        return distributionRepository.findById(distributionId).orElseThrow(() -> ApiException.notFound("Distribution"));
    }

    private void requireTenant(UUID tenantId) {
        if (PlatformTenant.is(tenantId) || !tenantRepository.existsById(tenantId)) {
            throw ApiException.notFound("Tenant");
        }
    }
}
