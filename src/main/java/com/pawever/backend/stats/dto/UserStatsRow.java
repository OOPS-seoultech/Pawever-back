package com.pawever.backend.stats.dto;

import com.pawever.backend.user.entity.ReferralType;

import java.time.LocalDateTime;

/**
 * 통계에 필요한 회원 항목만 뽑아 온다.
 *
 * 이름·연락처·이메일은 암호화 컬럼이라 엔티티를 통째로 읽으면 전부 복호화된다.
 * 통계에 쓰지도 않을 개인정보를 메모리에 올릴 이유가 없어 필요한 열만 가져온다.
 */
public record UserStatsRow(
        LocalDateTime createdAt,
        LocalDateTime deletedAt,
        boolean onboardingComplete,
        String kakaoId,
        String naverId,
        String appleId,
        ReferralType referralType,
        String ageRange,
        String gender,
        LocalDateTime notificationAgreedAt,
        LocalDateTime marketingAgreedAt
) {

    /**
     * 탈퇴하지 않은 회원.
     *
     * 탈퇴 시 소셜 ID·연령대·동의 시각이 모두 파기되므로(User.withdraw)
     * 채널·유입경로·동의율은 이 회원만 세야 뜻이 있다.
     */
    public boolean active() {
        return deletedAt == null;
    }
}
