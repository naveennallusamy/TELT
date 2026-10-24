package com.telt.dto.result;

import com.telt.entity.exam.ExamAccommodation;

import java.time.Instant;
import java.util.UUID;

public record AccommodationResponse(UUID accommodationId, UUID scheduleId, UUID userId, int extraMinutes, String reason,
                                    UUID grantedBy, Instant grantedAt) {

    public static AccommodationResponse from(ExamAccommodation a) {
        return new AccommodationResponse(a.getAccommodationId(), a.getScheduleId(), a.getUserId(), a.getExtraMinutes(),
                a.getReason(), a.getGrantedBy(), a.getGrantedAt());
    }
}
