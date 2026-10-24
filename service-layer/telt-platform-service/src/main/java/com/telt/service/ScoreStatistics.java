package com.telt.service;

import com.telt.constants.AttemptStatus;
import com.telt.constants.GradingStatus;
import com.telt.entity.exam.ExamAttempt;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;

/**
 * Score aggregation shared by reports and student results. Results are rounded to two decimals.
 */
final class ScoreStatistics {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private ScoreStatistics() {
    }

    /**
     * Each student's best closed, fully graded attempt; on a tie the earlier attempt (input is ordered by start).
     */
    static Map<UUID, ExamAttempt> bestGraded(Collection<ExamAttempt> attempts) {
        Map<UUID, ExamAttempt> best = new LinkedHashMap<>();
        for (ExamAttempt a : attempts) {
            if (a.getStatus() != AttemptStatus.IN_PROGRESS && a.getGradingStatus() == GradingStatus.GRADED) {
                best.merge(a.getUserId(), a, (x, y) -> y.getScore().compareTo(x.getScore()) > 0 ? y : x);
            }
        }
        return best;
    }

    record Standing(int rank, BigDecimal percentile) {
    }

    /**
     * Competition rank (1, 2, 2, 4) and midrank percentile, {@code (below + equal / 2) / n * 100}, of a score that is
     * part of {@code cohort}.
     */
    static Standing standing(BigDecimal score, List<BigDecimal> cohort) {
        long above = cohort.stream().filter(s -> s.compareTo(score) > 0).count();
        long below = cohort.stream().filter(s -> s.compareTo(score) < 0).count();
        long equal = cohort.size() - above - below;
        BigDecimal percentile = BigDecimal.valueOf(2 * below + equal).multiply(BigDecimal.valueOf(50))
                .divide(BigDecimal.valueOf(cohort.size()), 2, RoundingMode.HALF_UP);
        return new Standing((int) above + 1, percentile);
    }

    static BigDecimal mean(Collection<BigDecimal> values) {
        return values.isEmpty() ? null : round(exactMean(values));
    }

    static BigDecimal median(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int mid = sorted.size() / 2;
        return round(sorted.size() % 2 == 1 ? sorted.get(mid)
                : sorted.get(mid - 1).add(sorted.get(mid)).divide(BigDecimal.TWO, MC));
    }

    /**
     * Population standard deviation.
     */
    static BigDecimal standardDeviation(Collection<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        BigDecimal mean = exactMean(values);
        BigDecimal squares = values.stream().map(v -> v.subtract(mean).pow(2)).reduce(BigDecimal.ZERO, BigDecimal::add);
        return round(squares.divide(BigDecimal.valueOf(values.size()), MC).sqrt(MC));
    }

    static BigDecimal percent(long part, long whole) {
        return whole == 0 ? null : BigDecimal.valueOf(part).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(whole), 2, RoundingMode.HALF_UP);
    }

    static BigDecimal percentage(BigDecimal score, BigDecimal max) {
        return score == null || max == null || max.signum() == 0 ? null
                : score.multiply(HUNDRED).divide(max, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal exactMean(Collection<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(values.size()), MC);
    }

    private static BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
