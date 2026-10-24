package com.telt.service;

import com.telt.access.ExamAccess;
import com.telt.constants.*;
import com.telt.dto.report.PlatformExamReport;
import com.telt.dto.report.ScheduleReport;
import com.telt.entity.exam.*;
import com.telt.entity.tenant.Department;
import com.telt.entity.tenant.Tenant;
import com.telt.entity.tenant.TenantMembership;
import com.telt.error.ApiException;
import com.telt.repository.*;
import com.telt.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Schedule reports for tenant staff ({@code REPORT_VIEW} / {@code REPORT_EXPORT} on the exam's department or tenant)
 * and super admins, plus the cross-tenant view of a distributed platform exam.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final int BANDS = 10;

    private final ExamScheduleRepository scheduleRepository;
    private final ExamRepository examRepository;
    private final ExamVersionRepository versionRepository;
    private final ExamSectionRepository sectionRepository;
    private final ExamQuestionSnapshotRepository snapshotRepository;
    private final ExamAssignmentRepository assignmentRepository;
    private final ExamAttemptRepository attemptRepository;
    private final AttemptAnswerRepository answerRepository;
    private final ExamDistributionRepository distributionRepository;
    private final TenantMembershipRepository membershipRepository;
    private final DepartmentRepository departmentRepository;
    private final TenantRepository tenantRepository;
    private final AttemptService attemptService;
    private final ExamAccess access;
    private final AuditService auditService;

    @Transactional
    public ScheduleReport schedule(UUID scheduleId) {
        return build(TenantContext.requireCurrentTenant(), scheduleId, Permission.REPORT_VIEW);
    }

    @Transactional
    public String exportCsv(UUID scheduleId) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        ScheduleReport report = build(tenantId, scheduleId, Permission.REPORT_EXPORT);
        auditService.record(tenantId, "REPORT_EXPORTED", "EXAM_SCHEDULE", scheduleId,
                Map.of("format", "csv", "rows", report.students().size()));
        return csv(report);
    }

    @Transactional
    public ScheduleReport tenantSchedule(UUID tenantId, UUID scheduleId) {
        if (PlatformTenant.is(tenantId) || !tenantRepository.existsById(tenantId)) {
            throw ApiException.notFound("Tenant");
        }
        ScheduleReport report = build(tenantId, scheduleId, null);
        auditService.record(tenantId, "PLATFORM_TENANT_VIEWED", "EXAM_SCHEDULE", scheduleId, Map.of("view", "report"));
        return report;
    }

    @Transactional
    public PlatformExamReport platformExam(UUID examId) {
        Exam source = examRepository.findByExamIdAndTenantId(examId, PlatformTenant.ID)
                .orElseThrow(() -> ApiException.notFound("Exam"));
        List<PlatformExamReport.TenantRow> rows = new ArrayList<>();
        List<BigDecimal> allPercentages = new ArrayList<>();
        long allPassed = 0;
        long allWithPassMark = 0;
        for (ExamDistribution d : distributionRepository.findBySourceExamIdOrderByDistributedAt(examId)) {
            UUID tenantId = d.getTenantId();
            List<ExamSchedule> schedules = scheduleRepository.findByTenantIdAndExamIdOrderByStartsAt(tenantId,
                    d.getExamId());
            List<BigDecimal> percentages = new ArrayList<>();
            long passed = 0;
            long withPassMark = 0;
            for (ExamSchedule s : schedules) {
                for (ExamAttempt a : ScoreStatistics.bestGraded(attemptRepository
                        .findByTenantIdAndScheduleIdOrderByStartedAt(tenantId, s.getScheduleId())).values()) {
                    BigDecimal p = ScoreStatistics.percentage(a.getScore(), a.getMaxScore());
                    if (p != null) {
                        percentages.add(p);
                    }
                    BigDecimal pass = a.getSettings().passMarks();
                    if (pass != null) {
                        withPassMark++;
                        passed += a.getScore().compareTo(pass) >= 0 ? 1 : 0;
                    }
                }
            }
            String name = tenantRepository.findById(tenantId).map(Tenant::getTenantName).orElse(null);
            rows.add(new PlatformExamReport.TenantRow(tenantId, name, d.getStatus(), d.getExamId(), schedules.size(),
                    percentages.size(), ScoreStatistics.mean(percentages),
                    ScoreStatistics.percent(passed, withPassMark)));
            allPercentages.addAll(percentages);
            allPassed += passed;
            allWithPassMark += withPassMark;
            auditService.record(tenantId, "PLATFORM_TENANT_VIEWED", "EXAM", d.getExamId(),
                    Map.of("view", "platform-exam-report"));
        }
        return new PlatformExamReport(examId, source.getCode(), source.getTitle(), rows.size(), allPercentages.size(),
                ScoreStatistics.mean(allPercentages), ScoreStatistics.percent(allPassed, allWithPassMark), rows);
    }

    private ScheduleReport build(UUID tenantId, UUID scheduleId, Permission permission) {
        ExamSchedule schedule = scheduleRepository.findByScheduleIdAndTenantId(scheduleId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Schedule"));
        Exam exam = examRepository.findByExamIdAndTenantId(schedule.getExamId(), tenantId).orElseThrow();
        if (permission != null && !access.can(permission, exam)) {
            throw ApiException.notFound("Schedule");
        }
        ExamVersion version = versionRepository.findByTenantIdAndExamIdAndVersionNo(tenantId, exam.getExamId(),
                schedule.getVersionNo()).orElseThrow();
        BigDecimal passMarks = schedule.getOverrides().applyTo(version.getSettings()).normalized().passMarks();

        List<ExamSection> sections = sectionRepository.findByTenantIdAndExamIdAndVersionNoOrderByPosition(tenantId,
                exam.getExamId(), schedule.getVersionNo());
        Map<UUID, Integer> sectionIndex = new HashMap<>();
        for (int i = 0; i < sections.size(); i++) {
            sectionIndex.put(sections.get(i).getSectionId(), i);
        }
        List<ExamQuestionSnapshot> questions = snapshotRepository.findByTenantIdAndExamIdAndVersionNoOrderByPosition(
                        tenantId, exam.getExamId(), schedule.getVersionNo()).stream()
                .sorted(Comparator.comparing((ExamQuestionSnapshot q) -> sectionIndex.getOrDefault(q.getSectionId(), 0))
                        .thenComparingInt(ExamQuestionSnapshot::getPosition))
                .toList();
        BigDecimal maxScore = questions.stream().map(ExamQuestionSnapshot::getMarks).reduce(BigDecimal.ZERO,
                BigDecimal::add);

        List<ExamAttempt> attempts = attemptService.closeOverdue(tenantId, scheduleId);
        Map<UUID, List<ExamAttempt>> byUser = attempts.stream()
                .collect(Collectors.groupingBy(ExamAttempt::getUserId, LinkedHashMap::new, Collectors.toList()));
        Map<UUID, ExamAttempt> best = ScoreStatistics.bestGraded(attempts);
        List<BigDecimal> cohort = best.values().stream().map(ExamAttempt::getScore).toList();

        Map<UUID, TenantMembership> members = membershipRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(m -> m.getUser().getId(), Function.identity(), (a, b) -> a));
        Set<UUID> assigned = assignedStudents(tenantId, scheduleId, members.values());
        Set<UUID> users = new LinkedHashSet<>(assigned);
        users.addAll(byUser.keySet());

        List<ScheduleReport.StudentRow> students = new ArrayList<>();
        int pendingGrading = 0;
        for (UUID userId : users) {
            List<ExamAttempt> own = byUser.getOrDefault(userId, List.of());
            ExamAttempt b = best.get(userId);
            String status = own.isEmpty() ? "ABSENT" : b != null ? "GRADED"
                    : own.stream().anyMatch(a -> a.getStatus() == AttemptStatus.IN_PROGRESS) ? "IN_PROGRESS"
                    : "PENDING_GRADING";
            pendingGrading += "PENDING_GRADING".equals(status) ? 1 : 0;
            TenantMembership m = members.get(userId);
            ScoreStatistics.Standing standing = b == null ? null : ScoreStatistics.standing(b.getScore(), cohort);
            students.add(new ScheduleReport.StudentRow(userId, m == null ? null : m.getUser().getEmail(),
                    m == null ? null : m.getDepartmentId(), status, own.size(), b == null ? null : b.getAttemptId(),
                    b == null ? null : b.getScore(),
                    b == null ? null : ScoreStatistics.percentage(b.getScore(), b.getMaxScore()),
                    b == null || passMarks == null ? null : b.getScore().compareTo(passMarks) >= 0,
                    standing == null ? null : standing.rank(), standing == null ? null : standing.percentile()));
        }
        students.sort(Comparator.comparing(ScheduleReport.StudentRow::rank, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ScheduleReport.StudentRow::email, Comparator.nullsLast(Comparator.naturalOrder())));

        int absent = (int) assigned.stream().filter(u -> !byUser.containsKey(u)).count();
        ScheduleReport.Counts counts = new ScheduleReport.Counts(assigned.size(), byUser.size(), best.size(),
                pendingGrading, absent, count(attempts, AttemptStatus.IN_PROGRESS),
                count(attempts, AttemptStatus.SUBMITTED), count(attempts, AttemptStatus.EXPIRED));
        ScheduleReport.Stats stats = new ScheduleReport.Stats(cohort.size(), ScoreStatistics.mean(cohort),
                ScoreStatistics.median(cohort), cohort.stream().min(Comparator.naturalOrder()).orElse(null),
                cohort.stream().max(Comparator.naturalOrder()).orElse(null), ScoreStatistics.standardDeviation(cohort),
                passMarks == null ? null : ScoreStatistics.percent(
                        cohort.stream().filter(s -> s.compareTo(passMarks) >= 0).count(), cohort.size()));

        return new ScheduleReport(scheduleId, exam.getExamId(), exam.getTitle(), schedule.getTitle(),
                schedule.getStartsAt(), schedule.getEndsAt(), maxScore, passMarks, counts, stats,
                distribution(best.values()), students, questionStats(tenantId, questions, sectionIndex, best));
    }

    private Set<UUID> assignedStudents(UUID tenantId, UUID scheduleId, Collection<TenantMembership> members) {
        Set<UUID> users = new LinkedHashSet<>();
        Set<String> departments = new HashSet<>();
        for (ExamAssignment a : assignmentRepository.findByTenantIdAndScheduleIdOrderByAssignedAt(tenantId, scheduleId)) {
            if (a.getUserId() != null) {
                users.add(a.getUserId());
            }
            if (a.getDepartmentId() != null) {
                departments.add("/" + a.getDepartmentId() + "/");
            }
        }
        if (!departments.isEmpty()) {
            Set<UUID> subtree = departmentRepository.findByTenantIdOrderByPath(tenantId).stream()
                    .filter(d -> departments.stream().anyMatch(p -> ("/" + d.getPath()).contains(p)))
                    .map(Department::getDepartmentId).collect(Collectors.toSet());
            members.stream().filter(m -> m.getMembershipType() == MembershipType.STUDENT && m.isActive()
                            && m.getDepartmentId() != null && subtree.contains(m.getDepartmentId()))
                    .forEach(m -> users.add(m.getUser().getId()));
        }
        return users;
    }

    private static List<ScheduleReport.Band> distribution(Collection<ExamAttempt> best) {
        int[] counts = new int[BANDS];
        for (ExamAttempt a : best) {
            BigDecimal p = ScoreStatistics.percentage(a.getScore(), a.getMaxScore());
            if (p != null) {
                counts[Math.clamp(p.intValue() / (100 / BANDS), 0, BANDS - 1)]++;
            }
        }
        List<ScheduleReport.Band> bands = new ArrayList<>();
        for (int i = 0; i < BANDS; i++) {
            bands.add(new ScheduleReport.Band(i * 100 / BANDS, (i + 1) * 100 / BANDS, counts[i]));
        }
        return bands;
    }

    private List<ScheduleReport.QuestionStat> questionStats(UUID tenantId, List<ExamQuestionSnapshot> questions,
                                                            Map<UUID, Integer> sectionIndex,
                                                            Map<UUID, ExamAttempt> best) {
        Map<UUID, List<AttemptAnswer>> bySnapshot = best.isEmpty() ? Map.of()
                : answerRepository.findByTenantIdAndAttemptIdIn(tenantId,
                        best.values().stream().map(ExamAttempt::getAttemptId).toList()).stream()
                .collect(Collectors.groupingBy(AttemptAnswer::getSnapshotId));
        List<UUID> ranked = best.values().stream().sorted(Comparator.comparing(ExamAttempt::getScore).reversed())
                .map(ExamAttempt::getAttemptId).toList();
        int group = ranked.size() / 3;
        Set<UUID> upper = new HashSet<>(ranked.subList(0, group));
        Set<UUID> lower = new HashSet<>(ranked.subList(ranked.size() - group, ranked.size()));

        List<ScheduleReport.QuestionStat> stats = new ArrayList<>();
        for (ExamQuestionSnapshot q : questions) {
            List<AttemptAnswer> answers = bySnapshot.getOrDefault(q.getSnapshotId(), List.of());
            List<AttemptAnswer> full = answers.stream().filter(a -> fullMarks(a, q)).toList();
            BigDecimal discrimination = group == 0 ? null
                    : ScoreStatistics.percent(full.stream().filter(a -> upper.contains(a.getAttemptId())).count(), group)
                    .subtract(ScoreStatistics.percent(full.stream().filter(a -> lower.contains(a.getAttemptId()))
                            .count(), group));
            Map<String, Long> options = null;
            if (!q.getContent().optionsOrEmpty().isEmpty()) {
                options = new LinkedHashMap<>();
                for (var option : q.getContent().optionsOrEmpty()) {
                    options.put(option.id(), answers.stream().filter(a -> a.getResponse() != null
                            && a.getResponse().selectedOptionIds() != null
                            && a.getResponse().selectedOptionIds().contains(option.id())).count());
                }
            }
            stats.add(new ScheduleReport.QuestionStat(q.getSnapshotId(), sectionIndex.getOrDefault(q.getSectionId(), 0),
                    q.getPosition(), q.getQuestionType(), q.getContent().text(), q.getMarks(),
                    (int) answers.stream().filter(a -> a.getResponse() != null).count(),
                    ScoreStatistics.mean(answers.stream().map(a -> a.getMarksAwarded() == null ? BigDecimal.ZERO
                            : a.getMarksAwarded()).toList()),
                    ScoreStatistics.percent(full.size(), answers.size()), discrimination, options));
        }
        return stats;
    }

    private static boolean fullMarks(AttemptAnswer a, ExamQuestionSnapshot q) {
        return a.getMarksAwarded() != null && q.getMarks().signum() > 0 && a.getMarksAwarded().compareTo(q.getMarks()) >= 0;
    }

    private static int count(List<ExamAttempt> attempts, AttemptStatus status) {
        return (int) attempts.stream().filter(a -> a.getStatus() == status).count();
    }

    private static String csv(ScheduleReport report) {
        StringBuilder out = new StringBuilder(
                "userId,email,departmentId,status,attempts,score,maxScore,percentage,passed,rank,percentile\n");
        for (ScheduleReport.StudentRow r : report.students()) {
            out.append(String.join(",", cell(r.userId()), cell(r.email()), cell(r.departmentId()), cell(r.status()),
                    cell(r.attempts()), cell(r.score()), cell(report.maxScore()), cell(r.percentage()), cell(r.passed()),
                    cell(r.rank()), cell(r.percentile()))).append('\n');
        }
        return out.toString();
    }

    /**
     * RFC 4180 quoting; text starting with a formula character is prefixed with {@code '} so spreadsheets don't run it.
     */
    private static String cell(Object value) {
        if (value == null) {
            return "";
        }
        String s = value.toString();
        if (value instanceof String && !s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        return s.matches("(?s).*[,\"\\r\\n].*") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
