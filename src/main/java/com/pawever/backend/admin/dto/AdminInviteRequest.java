package com.pawever.backend.admin.dto;

import com.pawever.backend.admin.entity.AdminRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminInviteRequest(
        @NotBlank @Email @Size(max = 200) String email,
        @NotBlank @Size(max = 50) String name,
        @NotNull AdminRole role
) {
}
