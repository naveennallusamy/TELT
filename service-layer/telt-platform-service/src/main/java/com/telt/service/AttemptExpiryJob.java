package com.telt.service;

import com.telt.constants.AttemptStatus;
import com.telt.entity.exam.ExamAttempt;
import com.telt.persistence.RlsContext;
import com.telt.repository.ExamAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Closes and grades attempts whose deadline passed while nobody touched them. Safe on several instances: a lost
 * optimistic-lock race just means another instance (or the student) closed the attempt first.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttemptExpiryJob {

    private final ExamAttemptRepository attemptRepository;
    private final AttemptService attemptService;

    @Scheduled(fixedDelayString = "${telt.attempts.expiry-sweep-interval:60s}",
            initialDelayString = "${telt.attempts.expiry-sweep-interval:60s}")
    public void sweep() {
        List<UUID> overdue = RlsContext.asSystem(() -> attemptRepository
                .findTop200ByStatusAndDeadlineAtBeforeOrderByDeadlineAt(AttemptStatus.IN_PROGRESS, Instant.now())
                .stream().map(ExamAttempt::getAttemptId).toList());
        for (UUID attemptId : overdue) {
            try {
                RlsContext.asSystem(() -> attemptService.expireIfOverdue(attemptId));
            } catch (RuntimeException e) {
                log.debug("Attempt {} not expired by sweeper: {}", attemptId, e.getMessage());
            }
        }
    }
}
