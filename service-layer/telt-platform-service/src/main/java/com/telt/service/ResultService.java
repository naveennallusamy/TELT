package com.telt.service;

import com.telt.access.ExamAccess;
import com.telt.constants.*;
import com.telt.dto.result.ResultDecisionRequest;
import com.telt.dto.result.ScheduleResultResponse;
import com.telt.dto.result.StudentResultResponse;
import com.telt.entity.exam.*;
import com.telt.error.ApiException;
import com.telt.error.ErrorCode;
import com.telt.repository.*;
import com.telt.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * When students see results. {@code IMMEDIATE}: as soon as their attempt is closed and fully graded.
 * {@code AFTER_WINDOW}: additionally once the schedule window has ended. {@code MANUAL}: once the schedule's results
 * have been submitted, approved (by someone else when separation of duties is on) and published.
 */
@Service
@RequiredArgsConstructor
public class ResultService {

    private final ScheduleResultRepository resultRepository;
    private final ExamScheduleRepository scheduleRepository;
    private final ExamRepository examRepository;
    private final ExamVersionRepository versionRepository;
    private final ExamAttemptRepository attemptRepository;
    private final TenantRepository tenantRepository;
    private final AttemptService attemptService;
    private final ExamAccess access;
    private final AuditService auditService;

    @Transactional
    public ScheduleResultResponse status(UUID scheduleId) {
        Loaded l = load(scheduleId, Permission.RESULT_SUBMIT_FOR_APPROVAL, Permission.RESULT_APPROVE,
                Permission.RESULT_PUBLISH, Permission.RESPONSE_VIEW);
        return response(l);
    }

    @Transactional
    public ScheduleResultResponse submit(UUID scheduleId) {
        Loaded l = load(scheduleId, Permission.RESULT_SUBMIT_FOR_APPROVAL);
        if (l.release() != ResultRelease.MANUAL) {
            throw ApiException.conflict("Results of this schedule are released automatically (" + l.release() + ")");
        }
        if (Instant.now().isBefore(l.schedule().getEndsAt())) {
            throw ApiException.conflict("The exam window has not closed yet");
        }
        List<ExamAttempt> attempts = attemptService.closeOverdue(l.schedule().getTenantId(), scheduleId);
        if (attempts.stream().anyMatch(a -> a.getStatus() == AttemptStatus.IN_PROGRESS)) {
            throw ApiException.conflict("Some attempts are still in progress");
        }
        long pending = attempts.stream().filter(a -> a.getGradingStatus() == GradingStatus.PENDING_MANUAL).count();
        if (pending > 0) {
            throw ApiException.conflict(pending + " attempt(s) still need manual grading");
        }
        ScheduleResult result = l.result().orElseGet(() -> {
            ScheduleResult r = new ScheduleResult();
            r.setScheduleId(scheduleId);
            r.setTenantId(l.schedule().getTenantId());
            return r;
        });
        if (result.getStatus() != null && result.getStatus() != ResultStatus.REJECTED) {
            throw ApiException.conflict("Results are already " + result.getStatus());
        }
        result.setStatus(ResultStatus.PENDING_APPROVAL);
        result.setSubmittedBy(TenantContext.requireCurrentUserId());
        result.setSubmittedAt(Instant.now());
        result.setDecidedBy(null);
        result.setDecidedAt(null);
        result.setComment(null);
        resultRepository.save(result);
        auditService.record(result.getTenantId(), "RESULTS_SUBMITTED", "EXAM_SCHEDULE", scheduleId, Map.of());
        return response(new Loaded(l.schedule(), l.exam(), l.release(), Optional.of(result)));
    }

    @Transactional
    public ScheduleResultResponse decide(UUID scheduleId, ResultDecisionRequest request) {
        Loaded l = load(scheduleId, Permission.RESULT_APPROVE);
        ScheduleResult result = l.result().filter(r -> r.getStatus() == ResultStatus.PENDING_APPROVAL)
                .orElseThrow(() -> ApiException.conflict("Results are not waiting for approval"));
        UUID approver = TenantContext.requireCurrentUserId();
        boolean separation = tenantRepository.findById(result.getTenantId()).map(t -> t.isSeparationOfDuties())
                .orElse(true);
        if (separation && approver.equals(result.getSubmittedBy())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Submitters cannot approve their own results (separation of duties)");
        }
        result.setStatus(request.decision() == ReviewDecision.APPROVE ? ResultStatus.APPROVED : ResultStatus.REJECTED);
        result.setDecidedBy(approver);
        result.setDecidedAt(Instant.now());
        result.setComment(request.comment());
        auditService.record(result.getTenantId(), "RESULTS_" + result.getStatus(), "EXAM_SCHEDULE", scheduleId,
                Map.of());
        return response(l);
    }

    @Transactional
    public ScheduleResultResponse publish(UUID scheduleId) {
        Loaded l = load(scheduleId, Permission.RESULT_PUBLISH);
        ScheduleResult result = l.result().filter(r -> r.getStatus() == ResultStatus.APPROVED)
                .orElseThrow(() -> ApiException.conflict("Results must be approved before publishing"));
        result.setStatus(ResultStatus.PUBLISHED);
        result.setPublishedBy(TenantContext.requireCurrentUserId());
        result.setPublishedAt(Instant.now());
        auditService.record(result.getTenantId(), "RESULTS_PUBLISHED", "EXAM_SCHEDULE", scheduleId, Map.of());
        return response(l);
    }

    @Transactional
    public List<StudentResultResponse> mine() {
        UUID tenantId = TenantContext.requireCurrentTenant();
        List<ExamAttempt> attempts = attemptRepository.findByTenantIdAndUserIdOrderByStartedAtDesc(tenantId,
                TenantContext.requireCurrentUserId());
        Set<UUID> scheduleIds = new LinkedHashSet<>();
        attempts.forEach(a -> scheduleIds.add(a.getScheduleId()));
        List<StudentResultResponse> results = new ArrayList<>();
        for (UUID scheduleId : scheduleIds) {
            ExamSchedule schedule = scheduleRepository.findByScheduleIdAndTenantId(scheduleId, tenantId).orElseThrow();
            Optional<ScheduleResult> result = resultRepository.findByScheduleIdAndTenantId(scheduleId, tenantId);
            List<ExamAttempt> own = attempts.stream().filter(a -> a.getScheduleId().equals(scheduleId)).toList();
            own.forEach(a -> attemptService.expireIfOverdue(a.getAttemptId()));
            for (ExamAttempt a : own) {
                if (released(a, schedule, result)) {
                    Exam exam = examRepository.findByExamIdAndTenantId(a.getExamId(), tenantId).orElseThrow();
                    results.add(attemptService.result(a, exam));
                }
            }
        }
        return results;
    }

    private static boolean released(ExamAttempt a, ExamSchedule schedule, Optional<ScheduleResult> result) {
        if (a.getStatus() == AttemptStatus.IN_PROGRESS || a.getGradingStatus() != GradingStatus.GRADED) {
            return false;
        }
        return switch (a.getSettings().resultRelease()) {
            case IMMEDIATE -> true;
            case AFTER_WINDOW -> !Instant.now().isBefore(schedule.getEndsAt());
            case MANUAL -> result.map(r -> r.getStatus() == ResultStatus.PUBLISHED).orElse(false);
        };
    }

    private record Loaded(ExamSchedule schedule, Exam exam, ResultRelease release, Optional<ScheduleResult> result) {
    }

    private Loaded load(UUID scheduleId, Permission... anyOf) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        ExamSchedule schedule = scheduleRepository.findByScheduleIdAndTenantId(scheduleId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Schedule"));
        Exam exam = examRepository.findByExamIdAndTenantId(schedule.getExamId(), tenantId).orElseThrow();
        if (Arrays.stream(anyOf).noneMatch(p -> access.can(p, exam))) {
            throw ApiException.notFound("Schedule");
        }
        ExamVersion version = versionRepository.findByTenantIdAndExamIdAndVersionNo(tenantId, schedule.getExamId(),
                schedule.getVersionNo()).orElseThrow();
        ResultRelease release = schedule.getOverrides().applyTo(version.getSettings()).normalized().resultRelease();
        return new Loaded(schedule, exam, release, resultRepository.findByScheduleIdAndTenantId(scheduleId, tenantId));
    }

    private ScheduleResultResponse response(Loaded l) {
        List<ExamAttempt> attempts = attemptService.closeOverdue(l.schedule().getTenantId(),
                l.schedule().getScheduleId());
        long open = attempts.stream().filter(a -> a.getStatus() == AttemptStatus.IN_PROGRESS).count();
        long pending = attempts.stream().filter(a -> a.getGradingStatus() == GradingStatus.PENDING_MANUAL).count();
        ScheduleResult r = l.result().orElse(null);
        boolean released = switch (l.release()) {
            case IMMEDIATE -> true;
            case AFTER_WINDOW -> !Instant.now().isBefore(l.schedule().getEndsAt());
            case MANUAL -> r != null && r.getStatus() == ResultStatus.PUBLISHED;
        };
        return new ScheduleResultResponse(l.schedule().getScheduleId(), l.release(), r == null ? null : r.getStatus(),
                released, attempts.size(), open, pending, r == null ? null : r.getSubmittedBy(),
                r == null ? null : r.getSubmittedAt(), r == null ? null : r.getDecidedBy(),
                r == null ? null : r.getDecidedAt(), r == null ? null : r.getComment(),
                r == null ? null : r.getPublishedBy(), r == null ? null : r.getPublishedAt());
    }
}
