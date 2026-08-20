package com.pawever.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminAcceptInviteRequest(
        @NotBlank String inviteToken,
        @NotBlank String password
) {
}
