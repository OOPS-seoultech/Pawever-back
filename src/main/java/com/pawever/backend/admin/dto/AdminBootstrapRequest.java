package com.pawever.backend.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminBootstrapRequest(
        @NotBlank @Email @Size(max = 200) String email,
        @NotBlank @Size(max = 50) String name
) {
}
