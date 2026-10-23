package com.telt.service;

import com.telt.access.AuthorizationService;
import com.telt.access.AuthorizationSnapshot;
import com.telt.access.ExamAccess;
import com.telt.constants.*;
import com.telt.dto.schedule.*;
import com.telt.entity.exam.*;
import com.telt.entity.tenant.Department;
import com.telt.entity.tenant.TenantMembership;
import com.telt.error.ApiException;
import com.telt.error.ErrorCode;
import com.telt.repository.*;
import com.telt.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Schedules of published exam versions, their assignment to students and departments, and the student's view of
 * assigned exams. Distributed platform exams only accept the overrides their distribution policy allows.
 */
@Service
@RequiredArgsConstructor
public class ExamScheduleService {

    private final ExamRepository examRepository;
    private final ExamVersionRepository versionRepository;
    private final ExamSectionRepository sectionRepository;
    private final ExamQuestionSnapshotRepository snapshotRepository;
    private final ExamScheduleRepository scheduleRepository;
    private final ExamAssignmentRepository assignmentRepository;
    private final ExamDistributionRepository distributionRepository;
    private final TenantMembershipRepository membershipRepository;
    private final DepartmentRepository departmentRepository;
    private final ExamAccess access;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<ScheduleResponse> list(UUID examId) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        List<ExamSchedule> schedules = examId == null ? scheduleRepository.findByTenantIdOrderByStartsAt(tenantId)
                : scheduleRepository.findByTenantIdAndExamIdOrderByStartsAt(tenantId, examId);
        Map<UUID, Optional<Exam>> exams = new HashMap<>();
        return schedules.stream().filter(s -> exams.computeIfAbsent(s.getExamId(),
                        id -> examRepository.findByExamIdAndTenantId(id, tenantId)).filter(this::visible).isPresent())
                .map(this::view).toList();
    }

    @Transactional
    public ScheduleResponse create(ScheduleRequest request) {
        if (request.examId() == null) {
            throw ApiException.badRequest("examId is required");
        }
        UUID tenantId = TenantContext.requireCurrentTenant();
        Exam exam = examRepository.findByExamIdAndTenantId(request.examId(), tenantId).filter(this::visible)
                .orElseThrow(() -> ApiException.notFound("Exam"));
        access.require(Permission.EXAM_ASSIGN, exam);
        if (exam.getStatus() != RecordStatus.ACTIVE) {
            throw ApiException.conflict("Exam is archived");
        }
        if (exam.getPublishedVersion() == null) {
            throw ApiException.conflict("Only published exams can be scheduled");
        }
        ExamSchedule schedule = new ExamSchedule();
        schedule.setTenantId(tenantId);
        schedule.setExamId(exam.getExamId());
        schedule.setVersionNo(exam.getPublishedVersion());
        apply(schedule, exam, request);
        scheduleRepository.save(schedule);
        auditService.record(tenantId, "EXAM_SCHEDULED", "EXAM_SCHEDULE", schedule.getScheduleId(),
                Map.of("examId", exam.getExamId(), "versionNo", schedule.getVersionNo()));
        return view(schedule);
    }

    @Transactional(readOnly = true)
    public ScheduleResponse get(UUID scheduleId) {
        return view(load(scheduleId, null).schedule());
    }

    /**
     * Changes the window and overrides of a schedule that has not started yet.
     */
    @Transactional
    public ScheduleResponse update(UUID scheduleId, ScheduleRequest request) {
        Loaded loaded = load(scheduleId, Permission.EXAM_ASSIGN);
        ExamSchedule schedule = loaded.schedule();
        requireScheduled(schedule);
        if (!schedule.getStartsAt().isAfter(Instant.now())) {
            throw ApiException.conflict("Schedules can only be changed before they start");
        }
        apply(schedule, loaded.exam(), request);
        auditService.record(schedule.getTenantId(), "EXAM_SCHEDULE_UPDATED", "EXAM_SCHEDULE", scheduleId,
                Map.of("startsAt", schedule.getStartsAt().toString(), "endsAt", schedule.getEndsAt().toString()));
        return view(schedule);
    }

    @Transactional
    public ScheduleResponse cancel(UUID scheduleId) {
        ExamSchedule schedule = load(scheduleId, Permission.EXAM_ASSIGN).schedule();
        requireScheduled(schedule);
        schedule.setStatus(ScheduleStatus.CANCELLED);
        auditService.record(schedule.getTenantId(), "EXAM_SCHEDULE_CANCELLED", "EXAM_SCHEDULE", scheduleId, Map.of());
        return view(schedule);
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> assignments(UUID scheduleId) {
        ExamSchedule schedule = load(scheduleId, null).schedule();
        return assignmentRepository.findByTenantIdAndScheduleIdOrderByAssignedAt(schedule.getTenantId(), scheduleId)
                .stream().map(AssignmentResponse::from).toList();
    }

    /**
     * Adds students and departments; ones already assigned are skipped. Department-scoped staff can only assign
     * within their departments.
     */
    @Transactional
    public List<AssignmentResponse> assign(UUID scheduleId, AssignRequest request) {
        ExamSchedule schedule = load(scheduleId, Permission.EXAM_ASSIGN).schedule();
        requireScheduled(schedule);
        List<UUID> userIds = request.userIds() == null ? List.of() : request.userIds();
        List<UUID> departmentIds = request.departmentIds() == null ? List.of() : request.departmentIds();
        if (userIds.isEmpty() && departmentIds.isEmpty()) {
            throw ApiException.badRequest("Provide userIds and/or departmentIds");
        }
        UUID tenantId = schedule.getTenantId();
        AuthorizationSnapshot auth = authorizationService.current();
        UUID actor = TenantContext.requireCurrentUserId();
        Instant now = Instant.now();
        int added = 0;
        for (UUID userId : new LinkedHashSet<>(userIds)) {
            TenantMembership member = membershipRepository.findByTenantIdAndUser_Id(tenantId, userId)
                    .filter(m -> m.getMembershipType() == MembershipType.STUDENT && m.getStatus() == RecordStatus.ACTIVE)
                    .orElseThrow(() -> ApiException.badRequest("User " + userId + " is not an active student of this tenant"));
            requireScope(auth, member.getDepartmentId());
            if (!assignmentRepository.existsByTenantIdAndScheduleIdAndUserId(tenantId, scheduleId, userId)) {
                assignmentRepository.save(assignment(schedule, actor, now, userId, null));
                added++;
            }
        }
        for (UUID departmentId : new LinkedHashSet<>(departmentIds)) {
            departmentRepository.findByDepartmentIdAndTenantId(departmentId, tenantId)
                    .orElseThrow(() -> ApiException.badRequest("Department " + departmentId + " not found"));
            requireScope(auth, departmentId);
            if (!assignmentRepository.existsByTenantIdAndScheduleIdAndDepartmentId(tenantId, scheduleId, departmentId)) {
                assignmentRepository.save(assignment(schedule, actor, now, null, departmentId));
                added++;
            }
        }
        auditService.record(tenantId, "EXAM_ASSIGNED", "EXAM_SCHEDULE", scheduleId,
                Map.of("users", userIds.size(), "departments", departmentIds.size(), "added", added));
        return assignmentRepository.findByTenantIdAndScheduleIdOrderByAssignedAt(tenantId, scheduleId).stream()
                .map(AssignmentResponse::from).toList();
    }

    @Transactional
    public void unassign(UUID scheduleId, UUID assignmentId) {
        ExamSchedule schedule = load(scheduleId, Permission.EXAM_ASSIGN).schedule();
        ExamAssignment assignment = assignmentRepository.findByAssignmentIdAndTenantIdAndScheduleId(assignmentId,
                schedule.getTenantId(), scheduleId).orElseThrow(() -> ApiException.notFound("Assignment"));
        requireScope(authorizationService.current(), assignment.getDepartmentId() != null ? assignment.getDepartmentId()
                : membershipRepository.findByTenantIdAndUser_Id(schedule.getTenantId(), assignment.getUserId())
                .map(TenantMembership::getDepartmentId).orElse(null));
        assignmentRepository.delete(assignment);
        auditService.record(schedule.getTenantId(), "EXAM_UNASSIGNED", "EXAM_SCHEDULE", scheduleId,
                Map.of("assignmentId", assignmentId));
    }

    /**
     * Upcoming and running schedules assigned to the calling student, directly or through their department tree.
     */
    @Transactional(readOnly = true)
    public List<StudentExamResponse> myExams() {
        UUID tenantId = TenantContext.requireCurrentTenant();
        UUID userId = TenantContext.requireCurrentUserId();
        List<UUID> scheduleIds = assignedScheduleIds(tenantId, userId);
        if (scheduleIds.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        List<StudentExamResponse> result = new ArrayList<>();
        for (ExamSchedule s : scheduleRepository.findByTenantIdAndScheduleIdInAndStatusOrderByStartsAt(tenantId,
                scheduleIds, ScheduleStatus.SCHEDULED)) {
            if (!s.getEndsAt().isAfter(now)) {
                continue;
            }
            Exam exam = examRepository.findByExamIdAndTenantId(s.getExamId(), tenantId).orElseThrow();
            if (exam.getStatus() != RecordStatus.ACTIVE) {
                continue;
            }
            ExamVersion version = version(s);
            ExamSettings settings = s.getOverrides().applyTo(version.getSettings());
            Map<UUID, Long> counts = snapshotRepository.findByTenantIdAndExamIdAndVersionNoOrderByPosition(tenantId,
                    s.getExamId(), s.getVersionNo()).stream()
                    .collect(Collectors.groupingBy(ExamQuestionSnapshot::getSectionId, Collectors.counting()));
            List<StudentExamResponse.Section> sections = sectionRepository
                    .findByTenantIdAndExamIdAndVersionNoOrderByPosition(tenantId, s.getExamId(), s.getVersionNo()).stream()
                    .map(sec -> new StudentExamResponse.Section(sec.getTitle(), sec.getTimeLimitMinutes(),
                            counts.getOrDefault(sec.getSectionId(), 0L).intValue())).toList();
            result.add(new StudentExamResponse(s.getScheduleId(), exam.getExamId(), exam.getTitle(), s.getTitle(),
                    s.getStartsAt(), s.getEndsAt(), s.getTimeZone(), s.getLateEntryMinutes(), settings.durationMinutes(),
                    settings.maxAttempts(), settings.resultRelease(), settings.instructions(), version.getTotalMarks(),
                    sections));
        }
        return result;
    }

    /**
     * Schedules assigned to the student directly or through their department or any ancestor department.
     */
    List<UUID> assignedScheduleIds(UUID tenantId, UUID userId) {
        TenantMembership member = membershipRepository.findByTenantIdAndUser_Id(tenantId, userId)
                .filter(m -> m.getStatus() == RecordStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "Access denied"));
        List<UUID> departments = new ArrayList<>();
        if (member.getDepartmentId() != null) {
            departmentRepository.findByDepartmentIdAndTenantId(member.getDepartmentId(), tenantId).map(Department::getPath)
                    .ifPresent(path -> Arrays.stream(path.split("/")).filter(p -> !p.isBlank())
                            .map(UUID::fromString).forEach(departments::add));
        }
        if (departments.isEmpty()) {
            departments.add(new UUID(0, 0));
        }
        List<UUID> scheduleIds = assignmentRepository.findScheduleIdsFor(tenantId, userId, departments);
        return scheduleIds;
    }

    ScheduleResponse view(ExamSchedule schedule) {
        return ScheduleResponse.from(schedule, version(schedule).getSettings());
    }

    private void apply(ExamSchedule schedule, Exam exam, ScheduleRequest request) {
        try {
            ZoneId.of(request.timeZone());
        } catch (DateTimeException e) {
            throw ApiException.badRequest("Unknown time zone " + request.timeZone());
        }
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw ApiException.badRequest("endsAt must be after startsAt");
        }
        if (!request.endsAt().isAfter(Instant.now())) {
            throw ApiException.badRequest("The schedule window has already ended");
        }
        int lateEntry = request.lateEntryMinutes() == null ? 0 : request.lateEntryMinutes();
        ScheduleOverrides overrides = request.overrides() == null ? ScheduleOverrides.NONE : request.overrides();
        OverridePolicy policy = policy(exam);
        List<String> denied = new ArrayList<>();
        deny(denied, policy.duration(), overrides.durationMinutes() != null, "durationMinutes");
        deny(denied, policy.maxAttempts(), overrides.maxAttempts() != null, "maxAttempts");
        deny(denied, policy.resultRelease(), overrides.resultRelease() != null, "resultRelease");
        deny(denied, policy.shuffle(), overrides.shuffleQuestions() != null || overrides.shuffleOptions() != null,
                "shuffle");
        deny(denied, policy.instructions(), overrides.instructions() != null, "instructions");
        deny(denied, policy.lateEntry(), lateEntry > 0, "lateEntryMinutes");
        if (!denied.isEmpty()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "The exam's distribution policy does not allow changing "
                    + String.join(", ", denied));
        }
        int timed = sectionRepository.findByTenantIdAndExamIdAndVersionNoOrderByPosition(schedule.getTenantId(),
                        exam.getExamId(), schedule.getVersionNo()).stream().map(ExamSection::getTimeLimitMinutes)
                .filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        if (overrides.applyTo(version(schedule).getSettings()).durationMinutes() < timed) {
            throw ApiException.badRequest("durationMinutes is shorter than the section time limits");
        }
        schedule.setTitle(request.title());
        schedule.setStartsAt(request.startsAt());
        schedule.setEndsAt(request.endsAt());
        schedule.setTimeZone(request.timeZone());
        schedule.setLateEntryMinutes(lateEntry);
        schedule.setOverrides(overrides);
    }

    /**
     * Tenants own their exams outright; a distributed platform exam is limited by its distribution's policy.
     */
    private OverridePolicy policy(Exam exam) {
        if (exam.getSourceExamId() == null) {
            return OverridePolicy.ALL;
        }
        return distributionRepository.findByTenantIdAndExamId(exam.getTenantId(), exam.getExamId())
                .filter(d -> d.getStatus() == GrantStatus.ACTIVE).map(ExamDistribution::getOverridePolicy)
                .orElseThrow(() -> ApiException.conflict("The platform has withdrawn this exam"));
    }

    private static void deny(List<String> denied, Boolean allowed, boolean requested, String field) {
        if (requested && !Boolean.TRUE.equals(allowed)) {
            denied.add(field);
        }
    }

    private static void requireScope(AuthorizationSnapshot auth, UUID departmentId) {
        if (!auth.hasTenantWide(Permission.EXAM_ASSIGN)
                && (departmentId == null || !auth.allowsDepartment(Permission.EXAM_ASSIGN, departmentId))) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Outside your department scope");
        }
    }

    private static ExamAssignment assignment(ExamSchedule schedule, UUID actor, Instant now, UUID userId,
                                             UUID departmentId) {
        ExamAssignment a = new ExamAssignment();
        a.setTenantId(schedule.getTenantId());
        a.setScheduleId(schedule.getScheduleId());
        a.setUserId(userId);
        a.setDepartmentId(departmentId);
        a.setAssignedBy(actor);
        a.setAssignedAt(now);
        return a;
    }

    private record Loaded(ExamSchedule schedule, Exam exam) {
    }

    private Loaded load(UUID scheduleId, Permission permission) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        ExamSchedule schedule = scheduleRepository.findByScheduleIdAndTenantId(scheduleId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Schedule"));
        Exam exam = examRepository.findByExamIdAndTenantId(schedule.getExamId(), tenantId).filter(this::visible)
                .orElseThrow(() -> ApiException.notFound("Schedule"));
        if (permission != null) {
            access.require(permission, exam);
        }
        return new Loaded(schedule, exam);
    }

    private boolean visible(Exam exam) {
        return access.canView(exam) || access.can(Permission.EXAM_ASSIGN, exam);
    }

    private ExamVersion version(ExamSchedule s) {
        return versionRepository.findByTenantIdAndExamIdAndVersionNo(s.getTenantId(), s.getExamId(), s.getVersionNo())
                .orElseThrow(() -> ApiException.notFound("Exam version"));
    }

    private static void requireScheduled(ExamSchedule schedule) {
        if (schedule.getStatus() != ScheduleStatus.SCHEDULED) {
            throw ApiException.conflict("Schedule is cancelled");
        }
    }
}
