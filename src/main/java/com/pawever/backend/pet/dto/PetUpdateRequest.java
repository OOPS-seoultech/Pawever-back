package com.pawever.backend.pet.dto;

import com.pawever.backend.pet.entity.Gender;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class PetUpdateRequest {

    private Long breedId;

    @NotBlank(message = "이름은 필수입니다.")
    private String name;

    private LocalDate birthDate;

    private Gender gender;

    private Float weight;
}
