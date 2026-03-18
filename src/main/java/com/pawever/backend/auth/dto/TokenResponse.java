package com.pawever.backend.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class TokenResponse {
    private String accessToken;
    private Long userId;
    private boolean isNewUser;
    private Long selectedPetId;
    /** 온보딩(서비스 상 회원가입) 완료 여부 */
    private boolean onboardingComplete;
}
