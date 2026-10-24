package com.telt.service;

import com.telt.access.ExamAccess;
import com.telt.constants.*;
import com.telt.dto.attempt.*;
import com.telt.dto.result.AccommodationRequest;
import com.telt.dto.result.AccommodationResponse;
import com.telt.dto.result.StudentResultResponse;
import com.telt.entity.exam.*;
import com.telt.entity.question.QuestionOption;
import com.telt.error.ApiException;
import com.telt.grading.GraderRegistry;
import com.telt.repository.*;
import com.telt.util.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Students taking scheduled exams. Every student gets all questions of every section; with shuffling on, question
 * order (within a section) and option order are randomised once at start and stored with the attempt. Sections are
 * taken in order; a section with a time limit locks when it runs out, and the attempt closes at its deadline
 * (start + duration, capped by the schedule's end). Deadlines are enforced whenever the attempt is touched.
 */
@Service
@RequiredArgsConstructor
public class AttemptService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ExamAttemptRepository attemptRepository;
    private final AttemptAnswerRepository answerRepository;
    private final ExamScheduleRepository scheduleRepository;
    private final ExamRepository examRepository;
    private final ExamVersionRepository versionRepository;
    private final ExamSectionRepository sectionRepository;
    private final ExamQuestionSnapshotRepository snapshotRepository;
    private final ExamScheduleService scheduleService;
    private final GraderRegistry graders;
    private final ExamAccommodationRepository accommodationRepository;
    private final TenantMembershipRepository membershipRepository;
    private final ExamAccess access;
    private final AuditService auditService;

    /**
     * Starts an attempt, or returns the student's open attempt for the schedule.
     */
    @Transactional
    public AttemptResponse start(UUID scheduleId) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        UUID userId = TenantContext.requireCurrentUserId();
        if (!scheduleService.assignedScheduleIds(tenantId, userId).contains(scheduleId)) {
            throw ApiException.notFound("Schedule");
        }
        ExamSchedule schedule = scheduleRepository.findByScheduleIdAndTenantId(scheduleId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Schedule"));
        Optional<ExamAttempt> open = attemptRepository.findByTenantIdAndScheduleIdAndUserIdAndStatus(tenantId,
                scheduleId, userId, AttemptStatus.IN_PROGRESS);
        if (open.isPresent()) {
            Context ctx = context(open.get());
            refresh(ctx);
            if (ctx.attempt().getStatus() == AttemptStatus.IN_PROGRESS) {
                return view(ctx);
            }
            attemptRepository.flush();
        }
        Instant now = Instant.now();
        Exam exam = examRepository.findByExamIdAndTenantId(schedule.getExamId(), tenantId).orElseThrow();
        if (schedule.getStatus() != ScheduleStatus.SCHEDULED || exam.getStatus() != RecordStatus.ACTIVE) {
            throw ApiException.conflict("This exam is no longer available");
        }
        if (now.isBefore(schedule.getStartsAt())) {
            throw ApiException.conflict("The exam has not started yet");
        }
        if (!now.isBefore(schedule.getEndsAt())) {
            throw ApiException.conflict("The exam window has closed");
        }
        if (schedule.getLateEntryMinutes() > 0
                && now.isAfter(schedule.getStartsAt().plus(Duration.ofMinutes(schedule.getLateEntryMinutes())))) {
            throw ApiException.conflict("Late entry has closed");
        }
        ExamVersion version = versionRepository.findByTenantIdAndExamIdAndVersionNo(tenantId, schedule.getExamId(),
                schedule.getVersionNo()).orElseThrow();
        ExamSettings settings = schedule.getOverrides().applyTo(version.getSettings()).normalized();
        long previous = attemptRepository.countByTenantIdAndScheduleIdAndUserId(tenantId, scheduleId, userId);
        if (previous >= settings.maxAttempts()) {
            throw ApiException.conflict("No attempts left");
        }
        ExamAttempt attempt = new ExamAttempt();
        attempt.setTenantId(tenantId);
        attempt.setScheduleId(scheduleId);
        attempt.setExamId(schedule.getExamId());
        attempt.setVersionNo(schedule.getVersionNo());
        attempt.setUserId(userId);
        attempt.setAttemptNo((int) previous + 1);
        attempt.setSettings(settings);
        attempt.setStartedAt(now);
        int extra = accommodationRepository.findByTenantIdAndScheduleIdAndUserId(tenantId, scheduleId, userId)
                .map(ExamAccommodation::getExtraMinutes).orElse(0);
        attempt.setExtraMinutes(extra);
        Instant byDuration = now.plus(Duration.ofMinutes(settings.durationMinutes() + extra));
        Instant windowEnd = schedule.getEndsAt().plus(Duration.ofMinutes(extra));
        attempt.setDeadlineAt(byDuration.isBefore(windowEnd) ? byDuration : windowEnd);
        attempt.setSectionStartedAt(now);
        attempt.setMaxScore(version.getTotalMarks() == null ? BigDecimal.ZERO : version.getTotalMarks());
        attemptRepository.saveAndFlush(attempt);

        List<ExamSection> sections = sections(attempt);
        Map<UUID, List<ExamQuestionSnapshot>> bySection = snapshots(attempt).values().stream()
                .sorted(Comparator.comparingInt(ExamQuestionSnapshot::getPosition))
                .collect(Collectors.groupingBy(ExamQuestionSnapshot::getSectionId, LinkedHashMap::new, Collectors.toList()));
        List<AttemptAnswer> answers = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            List<ExamQuestionSnapshot> questions = new ArrayList<>(bySection.getOrDefault(sections.get(i).getSectionId(),
                    List.of()));
            if (settings.shuffleQuestions()) {
                Collections.shuffle(questions, RANDOM);
            }
            for (int j = 0; j < questions.size(); j++) {
                ExamQuestionSnapshot q = questions.get(j);
                List<String> options = new ArrayList<>(q.getContent().optionsOrEmpty().stream().map(QuestionOption::id)
                        .toList());
                if (settings.shuffleOptions()) {
                    Collections.shuffle(options, RANDOM);
                }
                AttemptAnswer answer = new AttemptAnswer();
                answer.setTenantId(tenantId);
                answer.setAttemptId(attempt.getAttemptId());
                answer.setSnapshotId(q.getSnapshotId());
                answer.setSectionIndex(i);
                answer.setPosition(j);
                answer.setOptionOrder(options.isEmpty() ? null : options);
                answers.add(answer);
            }
        }
        answerRepository.saveAllAndFlush(answers);
        return view(new Context(attempt, sections, snapshots(attempt), answers));
    }

    @Transactional
    public AttemptResponse get(UUID attemptId) {
        Context ctx = context(own(attemptId));
        refresh(ctx);
        return view(ctx);
    }

    @Transactional
    public List<AttemptResponse> mine() {
        UUID tenantId = TenantContext.requireCurrentTenant();
        return attemptRepository.findByTenantIdAndUserIdOrderByStartedAtDesc(tenantId,
                TenantContext.requireCurrentUserId()).stream().map(a -> {
            Context ctx = context(a);
            refresh(ctx);
            return view(ctx);
        }).toList();
    }

    /**
     * Autosave for questions of the current section.
     */
    @Transactional
    public AttemptResponse save(UUID attemptId, SaveAnswersRequest request) {
        Context ctx = context(own(attemptId));
        refresh(ctx);
        requireOpen(ctx.attempt());
        Map<UUID, AttemptAnswer> answers = ctx.answers().stream()
                .collect(Collectors.toMap(AttemptAnswer::getSnapshotId, Function.identity()));
        Instant now = Instant.now();
        for (SaveAnswersRequest.Answer a : request.answers()) {
            AttemptAnswer answer = answers.get(a.snapshotId());
            if (answer == null) {
                throw ApiException.badRequest("Question " + a.snapshotId() + " is not part of this attempt");
            }
            if (answer.getSectionIndex() != ctx.attempt().getCurrentSection()) {
                throw ApiException.conflict("Question " + a.snapshotId() + " is not in the current section");
            }
            validate(ctx.snapshots().get(a.snapshotId()), a.response());
            answer.setResponse(a.response());
            answer.setSavedAt(now);
        }
        return view(ctx);
    }

    /**
     * Locks the current section and moves to the next one.
     */
    @Transactional
    public AttemptResponse nextSection(UUID attemptId) {
        Context ctx = context(own(attemptId));
        refresh(ctx);
        requireOpen(ctx.attempt());
        if (ctx.attempt().getCurrentSection() >= ctx.sections().size() - 1) {
            throw ApiException.badRequest("This is the last section; submit the attempt instead");
        }
        ctx.attempt().setCurrentSection(ctx.attempt().getCurrentSection() + 1);
        ctx.attempt().setSectionStartedAt(Instant.now());
        return view(ctx);
    }

    @Transactional
    public AttemptResponse submit(UUID attemptId) {
        Context ctx = context(own(attemptId));
        refresh(ctx);
        requireOpen(ctx.attempt());
        finish(ctx, AttemptStatus.SUBMITTED, Instant.now());
        return view(ctx);
    }

    @Transactional
    public List<AttemptSummary> forSchedule(UUID scheduleId) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        ExamSchedule schedule = scheduleRepository.findByScheduleIdAndTenantId(scheduleId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Schedule"));
        Exam exam = examRepository.findByExamIdAndTenantId(schedule.getExamId(), tenantId).orElseThrow();
        if (!access.can(Permission.RESPONSE_VIEW, exam) && !access.can(Permission.EXAM_MONITOR_LIVE, exam)) {
            throw ApiException.notFound("Schedule");
        }
        return attemptRepository.findByTenantIdAndScheduleIdOrderByStartedAt(tenantId, scheduleId).stream().map(a -> {
            Context ctx = context(a);
            refresh(ctx);
            return summary(ctx);
        }).toList();
    }

    @Transactional
    public AttemptReviewResponse review(UUID attemptId) {
        Context ctx = context(staff(attemptId, Permission.RESPONSE_VIEW));
        refresh(ctx);
        return review(ctx);
    }

    /**
     * Marks an answer that needs a person (descriptive answers). Re-grading replaces the previous marks.
     */
    @Transactional
    public AttemptReviewResponse grade(UUID attemptId, UUID snapshotId, GradeRequest request) {
        Context ctx = context(staff(attemptId, Permission.GRADE_DESCRIPTIVE));
        refresh(ctx);
        if (ctx.attempt().getStatus() == AttemptStatus.IN_PROGRESS) {
            throw ApiException.conflict("The attempt is still in progress");
        }
        AttemptAnswer answer = ctx.answers().stream().filter(a -> a.getSnapshotId().equals(snapshotId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Answer"));
        if (answer.isAutoGraded()) {
            throw ApiException.conflict("This answer was graded automatically");
        }
        ExamQuestionSnapshot question = ctx.snapshots().get(snapshotId);
        if (request.marks().compareTo(question.getMarks()) > 0) {
            throw ApiException.badRequest("marks exceed the question's " + question.getMarks());
        }
        answer.setMarksAwarded(request.marks());
        answer.setFeedback(request.feedback());
        answer.setGradedBy(TenantContext.requireCurrentUserId());
        answer.setGradedAt(Instant.now());
        score(ctx);
        auditService.record(ctx.attempt().getTenantId(), "ANSWER_GRADED", "EXAM_ATTEMPT", attemptId,
                Map.of("snapshotId", snapshotId, "marks", request.marks()));
        return review(ctx);
    }

    /**
     * Grants or changes a student's extra time; an attempt already in progress gets the difference added.
     */
    @Transactional
    public AccommodationResponse accommodate(UUID scheduleId, UUID userId, AccommodationRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        ExamSchedule schedule = scheduleRepository.findByScheduleIdAndTenantId(scheduleId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Schedule"));
        Exam exam = examRepository.findByExamIdAndTenantId(schedule.getExamId(), tenantId).orElseThrow();
        if (!access.can(Permission.ATTEMPT_ACCOMMODATE, exam)) {
            throw ApiException.notFound("Schedule");
        }
        membershipRepository.findByTenantIdAndUser_Id(tenantId, userId)
                .filter(m -> m.getMembershipType() == MembershipType.STUDENT)
                .orElseThrow(() -> ApiException.badRequest("User " + userId + " is not a student of this tenant"));
        ExamAccommodation accommodation = accommodationRepository.findByTenantIdAndScheduleIdAndUserId(tenantId,
                scheduleId, userId).orElseGet(() -> {
            ExamAccommodation a = new ExamAccommodation();
            a.setTenantId(tenantId);
            a.setScheduleId(scheduleId);
            a.setUserId(userId);
            return a;
        });
        accommodation.setExtraMinutes(request.extraMinutes());
        accommodation.setReason(request.reason());
        accommodation.setGrantedBy(TenantContext.requireCurrentUserId());
        accommodation.setGrantedAt(Instant.now());
        accommodationRepository.save(accommodation);
        attemptRepository.findByTenantIdAndScheduleIdAndUserIdAndStatus(tenantId, scheduleId, userId,
                AttemptStatus.IN_PROGRESS).ifPresent(a -> {
            Context ctx = context(a);
            refresh(ctx);
            if (a.getStatus() == AttemptStatus.IN_PROGRESS) {
                a.setDeadlineAt(a.getDeadlineAt().plus(Duration.ofMinutes(request.extraMinutes() - a.getExtraMinutes())));
                a.setExtraMinutes(request.extraMinutes());
            }
        });
        auditService.record(tenantId, "ATTEMPT_ACCOMMODATED", "EXAM_SCHEDULE", scheduleId,
                Map.of("userId", userId, "extraMinutes", request.extraMinutes()));
        return AccommodationResponse.from(accommodation);
    }

    @Transactional(readOnly = true)
    public List<AccommodationResponse> accommodations(UUID scheduleId) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        ExamSchedule schedule = scheduleRepository.findByScheduleIdAndTenantId(scheduleId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Schedule"));
        Exam exam = examRepository.findByExamIdAndTenantId(schedule.getExamId(), tenantId).orElseThrow();
        if (!access.can(Permission.ATTEMPT_ACCOMMODATE, exam) && !access.can(Permission.EXAM_MONITOR_LIVE, exam)) {
            throw ApiException.notFound("Schedule");
        }
        return accommodationRepository.findByTenantIdAndScheduleIdOrderByGrantedAt(tenantId, scheduleId).stream()
                .map(AccommodationResponse::from).toList();
    }

    /**
     * Used by the background sweeper (runs with RLS bypass). Returns whether the attempt was closed.
     */
    @Transactional
    public boolean expireIfOverdue(UUID attemptId) {
        ExamAttempt attempt = attemptRepository.findById(attemptId).orElse(null);
        if (attempt == null || attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            return false;
        }
        refresh(context(attempt));
        return attempt.getStatus() != AttemptStatus.IN_PROGRESS;
    }

    /**
     * All attempts of a schedule with overdue ones closed; no access check.
     */
    @Transactional
    public List<ExamAttempt> closeOverdue(UUID tenantId, UUID scheduleId) {
        List<ExamAttempt> attempts = attemptRepository.findByTenantIdAndScheduleIdOrderByStartedAt(tenantId, scheduleId);
        attempts.forEach(a -> refresh(context(a)));
        return attempts;
    }

    /**
     * Super-admin view of a tenant's attempts; the caller checks platform permission and audits.
     */
    @Transactional
    public List<AttemptSummary> summaries(UUID tenantId, UUID scheduleId) {
        return closeOverdue(tenantId, scheduleId).stream().map(a -> summary(context(a))).toList();
    }

    @Transactional
    public AttemptReviewResponse review(UUID tenantId, UUID attemptId) {
        ExamAttempt attempt = attemptRepository.findByAttemptIdAndTenantId(attemptId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Attempt"));
        Context ctx = context(attempt);
        refresh(ctx);
        return review(ctx);
    }

    StudentResultResponse result(ExamAttempt attempt, Exam exam) {
        Context ctx = context(attempt);
        BigDecimal pass = attempt.getSettings().passMarks();
        List<StudentResultResponse.Answer> answers = ctx.answers().stream().map(a -> {
            ExamQuestionSnapshot q = ctx.snapshots().get(a.getSnapshotId());
            return new StudentResultResponse.Answer(a.getSectionIndex(), a.getPosition(), q.getQuestionType(),
                    q.getContent().text(), q.getMarks(), a.getMarksAwarded(), a.getFeedback());
        }).toList();
        return new StudentResultResponse(attempt.getAttemptId(), attempt.getScheduleId(), attempt.getExamId(),
                exam.getTitle(), attempt.getAttemptNo(), attempt.getStatus(), attempt.getSubmittedAt(), attempt.getScore(),
                attempt.getMaxScore(), pass, pass == null ? null : attempt.getScore().compareTo(pass) >= 0, answers,
                null, null, null);
    }

    private record Context(ExamAttempt attempt, List<ExamSection> sections, Map<UUID, ExamQuestionSnapshot> snapshots,
                           List<AttemptAnswer> answers) {
    }

    private Context context(ExamAttempt attempt) {
        return new Context(attempt, sections(attempt), snapshots(attempt),
                answerRepository.findByTenantIdAndAttemptIdOrderBySectionIndexAscPositionAsc(attempt.getTenantId(),
                        attempt.getAttemptId()));
    }

    /**
     * Applies section and attempt deadlines that have passed since the attempt was last touched.
     */
    private void refresh(Context ctx) {
        ExamAttempt attempt = ctx.attempt();
        Instant now = Instant.now();
        while (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            if (!now.isBefore(attempt.getDeadlineAt())) {
                finish(ctx, AttemptStatus.EXPIRED, attempt.getDeadlineAt());
                return;
            }
            Instant sectionDeadline = sectionDeadline(ctx);
            if (now.isBefore(sectionDeadline)) {
                return;
            }
            if (attempt.getCurrentSection() >= ctx.sections().size() - 1) {
                finish(ctx, AttemptStatus.EXPIRED, sectionDeadline);
                return;
            }
            attempt.setCurrentSection(attempt.getCurrentSection() + 1);
            attempt.setSectionStartedAt(sectionDeadline);
        }
    }

    private Instant sectionDeadline(Context ctx) {
        ExamAttempt attempt = ctx.attempt();
        Integer limit = ctx.sections().get(attempt.getCurrentSection()).getTimeLimitMinutes();
        if (limit == null) {
            return attempt.getDeadlineAt();
        }
        Instant end = attempt.getSectionStartedAt().plus(Duration.ofMinutes(limit));
        return end.isBefore(attempt.getDeadlineAt()) ? end : attempt.getDeadlineAt();
    }

    private void finish(Context ctx, AttemptStatus status, Instant at) {
        ExamAttempt attempt = ctx.attempt();
        attempt.setStatus(status);
        attempt.setSubmittedAt(at);
        boolean negative = Boolean.TRUE.equals(attempt.getSettings().negativeMarking());
        for (AttemptAnswer answer : ctx.answers()) {
            Optional<BigDecimal> marks = graders.grade(ctx.snapshots().get(answer.getSnapshotId()), answer.getResponse(),
                    negative);
            answer.setMarksAwarded(marks.orElse(null));
            answer.setAutoGraded(marks.isPresent());
        }
        score(ctx);
    }

    private static void score(Context ctx) {
        boolean pending = ctx.answers().stream().anyMatch(a -> a.getMarksAwarded() == null);
        ctx.attempt().setScore(ctx.answers().stream().map(AttemptAnswer::getMarksAwarded).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        ctx.attempt().setGradingStatus(pending ? GradingStatus.PENDING_MANUAL : GradingStatus.GRADED);
    }

    private static void validate(ExamQuestionSnapshot question, AnswerResponse response) {
        if (response == null || response.selectedOptionIds() == null) {
            return;
        }
        Set<String> ids = question.getContent().optionsOrEmpty().stream().map(QuestionOption::id)
                .collect(Collectors.toSet());
        if (!ids.containsAll(response.selectedOptionIds())) {
            throw ApiException.badRequest("Unknown option for question " + question.getSnapshotId());
        }
        if (question.getQuestionType() == QuestionType.SINGLE_CHOICE && response.selectedOptionIds().size() > 1) {
            throw ApiException.badRequest("Only one option may be selected for question " + question.getSnapshotId());
        }
    }

    private AttemptResponse view(Context ctx) {
        ExamAttempt attempt = ctx.attempt();
        boolean open = attempt.getStatus() == AttemptStatus.IN_PROGRESS;
        Map<Integer, List<AttemptAnswer>> bySection = ctx.answers().stream()
                .collect(Collectors.groupingBy(AttemptAnswer::getSectionIndex));
        List<AttemptResponse.Section> sections = new ArrayList<>();
        for (int i = 0; i < ctx.sections().size(); i++) {
            ExamSection section = ctx.sections().get(i);
            List<AttemptAnswer> answers = bySection.getOrDefault(i, List.of());
            AttemptResponse.SectionState state = !open || i < attempt.getCurrentSection() ? AttemptResponse.SectionState.DONE
                    : i == attempt.getCurrentSection() ? AttemptResponse.SectionState.CURRENT
                    : AttemptResponse.SectionState.UPCOMING;
            List<AttemptResponse.Question> questions = state == AttemptResponse.SectionState.CURRENT
                    ? answers.stream().map(a -> question(ctx.snapshots().get(a.getSnapshotId()), a)).toList() : List.of();
            sections.add(new AttemptResponse.Section(i, section.getTitle(), section.getTimeLimitMinutes(), state,
                    answers.size(), questions));
        }
        return new AttemptResponse(attempt.getAttemptId(), attempt.getScheduleId(), attempt.getExamId(),
                attempt.getAttemptNo(), attempt.getStatus(), attempt.getStartedAt(), attempt.getDeadlineAt(),
                attempt.getSubmittedAt(), attempt.getCurrentSection(), open ? sectionDeadline(ctx) : null,
                attempt.getSettings().instructions(), sections);
    }

    private static AttemptResponse.Question question(ExamQuestionSnapshot q, AttemptAnswer a) {
        Map<String, QuestionOption> options = q.getContent().optionsOrEmpty().stream()
                .collect(Collectors.toMap(QuestionOption::id, Function.identity()));
        List<QuestionOption> ordered = a.getOptionOrder() == null ? List.of()
                : a.getOptionOrder().stream().map(options::get).toList();
        return new AttemptResponse.Question(q.getSnapshotId(), a.getPosition(), q.getQuestionType(),
                q.getContent().text(), ordered, q.getMarks(), q.getNegativeMarks(), a.getResponse(), a.getSavedAt());
    }

    private AttemptSummary summary(Context ctx) {
        return AttemptSummary.from(ctx.attempt(), ctx.answers().stream().filter(a -> a.getResponse() != null).count());
    }

    private AttemptReviewResponse review(Context ctx) {
        Exam exam = examRepository.findByExamIdAndTenantId(ctx.attempt().getExamId(), ctx.attempt().getTenantId())
                .orElseThrow();
        boolean keys = access.can(Permission.ANSWER_KEY_VIEW, exam);
        return new AttemptReviewResponse(summary(ctx), ctx.answers().stream().map(a -> {
            ExamQuestionSnapshot q = ctx.snapshots().get(a.getSnapshotId());
            return new AttemptReviewResponse.Answer(q.getSnapshotId(), a.getSectionIndex(), a.getPosition(),
                    q.getQuestionType(), q.getContent(), keys && q.isAnswerKeyVisible() ? q.getAnswerKey() : null,
                    q.getMarks(), a.getResponse(), a.getSavedAt(), a.getMarksAwarded(), a.isAutoGraded(),
                    a.getGradedBy(), a.getFeedback());
        }).toList());
    }

    private ExamAttempt own(UUID attemptId) {
        return attemptRepository.findByAttemptIdAndTenantId(attemptId, TenantContext.requireCurrentTenant())
                .filter(a -> a.getUserId().equals(TenantContext.requireCurrentUserId()))
                .orElseThrow(() -> ApiException.notFound("Attempt"));
    }

    private ExamAttempt staff(UUID attemptId, Permission permission) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        ExamAttempt attempt = attemptRepository.findByAttemptIdAndTenantId(attemptId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Attempt"));
        Exam exam = examRepository.findByExamIdAndTenantId(attempt.getExamId(), tenantId).orElseThrow();
        if (!access.can(permission, exam)) {
            throw ApiException.notFound("Attempt");
        }
        return attempt;
    }

    private List<ExamSection> sections(ExamAttempt a) {
        return sectionRepository.findByTenantIdAndExamIdAndVersionNoOrderByPosition(a.getTenantId(), a.getExamId(),
                a.getVersionNo());
    }

    private Map<UUID, ExamQuestionSnapshot> snapshots(ExamAttempt a) {
        return snapshotRepository.findByTenantIdAndExamIdAndVersionNoOrderByPosition(a.getTenantId(), a.getExamId(),
                a.getVersionNo()).stream().collect(Collectors.toMap(ExamQuestionSnapshot::getSnapshotId,
                Function.identity()));
    }

    private static void requireOpen(ExamAttempt attempt) {
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw ApiException.conflict("The attempt is closed");
        }
    }
}
