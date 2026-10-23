package com.telt.dto.attempt;

import com.telt.entity.exam.AnswerResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Autosave: answers for questions of the current section. A null {@code response} clears the answer.
 */
public record SaveAnswersRequest(@NotEmpty @Size(max = 200) List<@Valid @NotNull Answer> answers) {

    public record Answer(@NotNull UUID snapshotId, @Valid AnswerResponse response) {
    }
}
