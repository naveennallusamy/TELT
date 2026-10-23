package com.telt.dto.platform;

import jakarta.validation.constraints.Size;

public record ReleaseRequest(@Size(max = 1000) String notes) {
}
