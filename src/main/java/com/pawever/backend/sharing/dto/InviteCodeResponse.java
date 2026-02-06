package com.pawever.backend.sharing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class InviteCodeResponse {
    private String inviteCode;
    private Long petId;
    private String petName;
}
