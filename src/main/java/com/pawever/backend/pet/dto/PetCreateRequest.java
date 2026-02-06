package com.pawever.backend.pet.dto;

import com.pawever.backend.pet.entity.Gender;
import com.pawever.backend.pet.entity.LifecycleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class PetCreateRequest {

    @NotNull(message = "품종 ID는 필수입니다.")
    private Long breedId;

    @NotBlank(message = "이름은 필수입니다.")
    private String name;

    private LocalDate birthDate;

    private Gender gender;

    private Float weight;

    @NotNull(message = "사용 시기를 선택해주세요.")
    private LifecycleStatus lifecycleStatus;
}
