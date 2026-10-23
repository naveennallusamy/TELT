package com.telt.dto.exam;

import java.util.List;
import java.util.UUID;

public record SectionResponse(UUID sectionId, int position, String title, Integer pickCount,
                              Integer timeLimitMinutes, List<ItemResponse> items) {
}
