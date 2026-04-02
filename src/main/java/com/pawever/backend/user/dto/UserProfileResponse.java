package com.pawever.backend.user.dto;

import com.pawever.backend.global.util.UrlUtils;
import com.pawever.backend.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class UserProfileResponse {
    private Long id;
    private String name;
    private String nickname;
    private String phone;
    private String email;
    private String gender;
    private String birthday;
    private String birthYear;
    private String ageRange;
    private String profileImageUrl;

    /** 알림(푸시) 수신 동의 여부 */
    private Boolean notificationEnabled;
    /** 마케팅 수신 동의 여부 */
    private Boolean marketingEnabled;

    /** 온보딩(서비스 상 회원가입) 완료 여부 */
    private Boolean onboardingComplete;

    public static UserProfileResponse from(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .nickname(user.getNickname())
                .phone(user.getPhone())
                .email(user.getEmail())
                .gender(user.getGender())
                .birthday(user.getBirthday())
                .birthYear(user.getBirthYear())
                .ageRange(user.getAgeRange())
                .profileImageUrl(UrlUtils.toHttpsUrl(user.getProfileImageUrl()))
                .notificationEnabled(user.isNotificationEnabled())
                .marketingEnabled(user.isMarketingEnabled())
                .onboardingComplete(user.isOnboardingComplete())
                .build();
    }
}
