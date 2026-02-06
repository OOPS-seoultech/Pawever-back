package com.pawever.backend.memorial.dto;

import com.pawever.backend.memorial.entity.Memorial;
import com.pawever.backend.pet.entity.Pet;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

@Getter
@Builder
@AllArgsConstructor
public class MemorialResponse {
    private Long memorialId;
    private Long petId;
    private String petName;
    private String petProfileImageUrl;
    private String gender;
    private Integer age;
    private LocalDateTime deathDate;

    public static MemorialResponse from(Memorial memorial) {
        Pet pet = memorial.getPet();
        Integer age = null;
        if (pet.getBirthDate() != null) {
            LocalDate endDate = memorial.getDeathDate() != null
                    ? memorial.getDeathDate().toLocalDate()
                    : LocalDate.now();
            age = Period.between(pet.getBirthDate(), endDate).getYears();
        }

        return MemorialResponse.builder()
                .memorialId(memorial.getId())
                .petId(pet.getId())
                .petName(pet.getName())
                .petProfileImageUrl(pet.getProfileImageUrl())
                .gender(pet.getGender() != null ? pet.getGender().name() : null)
                .age(age)
                .deathDate(memorial.getDeathDate())
                .build();
    }
}
