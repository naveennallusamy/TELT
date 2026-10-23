package com.telt.dto.question;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateBankRequest(@NotBlank @Size(max = 200) String name, @Size(max = 1000) String description,
                                UUID departmentId) {
}
