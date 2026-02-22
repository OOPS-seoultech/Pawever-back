package com.pawever.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class NaverLoginRequest {

    @NotBlank
    private String accessToken;
}
