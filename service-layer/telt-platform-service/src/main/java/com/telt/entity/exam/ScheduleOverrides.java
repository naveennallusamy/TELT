package com.telt.entity.exam;

import com.telt.constants.ResultRelease;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Settings changed for one schedule; null fields keep the exam version's value.
 */
public record ScheduleOverrides(@Min(1) @Max(1440) Integer durationMinutes,
                                @Min(1) @Max(100) Integer maxAttempts,
                                ResultRelease resultRelease,
                                Boolean shuffleQuestions,
                                Boolean shuffleOptions,
                                @Size(max = 10000) String instructions) {

    public static final ScheduleOverrides NONE = new ScheduleOverrides(null, null, null, null, null, null);

    public ExamSettings applyTo(ExamSettings base) {
        return new ExamSettings(durationMinutes != null ? durationMinutes : base.durationMinutes(),
                maxAttempts != null ? maxAttempts : base.maxAttempts(),
                shuffleQuestions != null ? shuffleQuestions : base.shuffleQuestions(),
                shuffleOptions != null ? shuffleOptions : base.shuffleOptions(),
                base.negativeMarking(), base.passMarks(),
                instructions != null ? instructions : base.instructions(),
                resultRelease != null ? resultRelease : base.resultRelease());
    }
}
