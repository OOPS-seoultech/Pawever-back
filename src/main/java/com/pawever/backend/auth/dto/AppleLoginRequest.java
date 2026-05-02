package com.pawever.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class AppleLoginRequest {

    @NotBlank
    private String identityToken;

    /** Apple이 첫 로그인 시 발급하는 1회용 코드. refresh_token 교환에 사용. */
    private String authorizationCode;
}
