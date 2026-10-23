package com.telt.dto.platform;

import com.telt.entity.question.BankRelease;

import java.time.Instant;
import java.util.UUID;

public record ReleaseResponse(UUID releaseId, UUID bankId, int releaseNo, String notes, int itemCount,
                              UUID publishedBy, Instant publishedAt) {

    public static ReleaseResponse from(BankRelease r) {
        return new ReleaseResponse(r.getReleaseId(), r.getBankId(), r.getReleaseNo(), r.getNotes(), r.getItemCount(),
                r.getPublishedBy(), r.getPublishedAt());
    }
}
