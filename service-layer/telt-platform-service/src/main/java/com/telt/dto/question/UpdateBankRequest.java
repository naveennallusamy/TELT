package com.telt.dto.question;

import com.telt.constants.RecordStatus;
import jakarta.validation.constraints.Size;

public record UpdateBankRequest(@Size(min = 1, max = 200) String name, @Size(max = 1000) String description,
                                RecordStatus status) {
}
