package com.pawever.backend.funeral.dto;

import com.pawever.backend.funeral.entity.RegistrationType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RegisterFuneralCompanyRequest {

    @NotNull(message = "반려동물 ID는 필수입니다.")
    private Long petId;

    @NotNull(message = "등록 타입은 필수입니다.")
    private RegistrationType type;
}
