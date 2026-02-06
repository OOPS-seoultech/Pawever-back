package com.pawever.backend.auth.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LoginRequest {
    // TODO: 소셜 로그인 구현 시 accessToken, provider 필드 사용
    private String provider;    // KAKAO or NAVER
    private String accessToken;
}
