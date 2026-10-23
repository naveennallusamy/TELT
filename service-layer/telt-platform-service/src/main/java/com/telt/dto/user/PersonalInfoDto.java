package com.telt.dto.user;

import com.telt.entity.user.UserPersonalInfo;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PersonalInfoDto(
        @Size(max = 20) String title,
        @Size(max = 100) String firstName,
        @Size(max = 100) String middleName,
        @Size(max = 100) String lastName,
        @Size(max = 20) String gender,
        @Past LocalDate dateOfBirth,
        @Size(max = 100) String nationality,
        @Size(max = 100) String designation) {

    public void applyTo(UserPersonalInfo info) {
        info.setTitle(title);
        info.setFirstName(firstName);
        info.setMiddleName(middleName);
        info.setLastName(lastName);
        info.setGender(gender);
        info.setDateOfBirth(dateOfBirth);
        info.setNationality(nationality);
        info.setDesignation(designation);
    }

    public static PersonalInfoDto from(UserPersonalInfo info) {
        return info == null ? null : new PersonalInfoDto(info.getTitle(), info.getFirstName(), info.getMiddleName(),
                info.getLastName(), info.getGender(), info.getDateOfBirth(), info.getNationality(),
                info.getDesignation());
    }
}
