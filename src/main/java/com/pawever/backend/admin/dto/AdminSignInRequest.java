package com.pawever.backend.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AdminSignInRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
