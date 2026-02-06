package com.pawever.backend.sharing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class InviteCodeRequest {

    @NotBlank(message = "초대코드는 필수입니다.")
    private String inviteCode;
}
