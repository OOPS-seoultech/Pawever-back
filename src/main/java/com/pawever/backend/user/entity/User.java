package com.pawever.backend.user.entity;

import com.pawever.backend.global.common.BaseTimeEntity;
import com.pawever.backend.global.common.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 500)
    private String name;

    private String nickname;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 500)
    private String phone;

    /** 전화번호 중복 검색용 Blind Index (HMAC-SHA256) */
    @Column(unique = true)
    private String phoneHash;

    /** 이메일 중복 검색용 Blind Index (HMAC-SHA256, Apple 로그인 전용) */
    @Column(unique = true)
    private String emailHash;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 500)
    private String email;

    private String gender;

    private String birthday;

    private String birthYear;

    private String ageRange;

    private String kakaoId;

    private String naverId;

    private String appleId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 500)
    private String appleRefreshToken;

    private String profileImageUrl;

    private Long selectedPetId;

    /**
     * 온보딩(서비스 상 회원가입) 완료 여부.
     * 소셜 로그인으로 User 레코드가 생성된 직후에는 false이며,
     * 사용자가 최초로 프로필 정보를 저장(닉네임 등록 등)하면 true로 변경된다.
     */
    @Column(nullable = false)
    private boolean onboardingComplete;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ReferralType referralType;

    @Column(length = 255)
    private String referralMemo;

    private LocalDateTime deletedAt;

    /** 알림(푸시) 수신 동의 시각. null이면 미동의 */
    private LocalDateTime notificationAgreedAt;

    /** 마케팅 수신 동의 시각. null이면 미동의 */
    private LocalDateTime marketingAgreedAt;

    /** FCM 디바이스 토큰 (푸시 알림 전송용) */
    @Column(length = 255)
    private String fcmToken;

    public void selectPet(Long petId) {
        this.selectedPetId = petId;
    }

    public void updateProfile(String name, String nickname, String phone, String phoneHash, String ageRange) {
        this.name = name;
        this.nickname = nickname;
        this.phone = phone;
        this.phoneHash = phoneHash;
        this.ageRange = ageRange;
    }

    public void updateReferral(ReferralType referralType, String referralMemo) {
        this.referralType = referralType;
        this.referralMemo = referralType == ReferralType.OTHER ? referralMemo : null;
    }

    public void updateProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void withdraw() {
        this.deletedAt = LocalDateTime.now();
        // 개인정보보호법 제21조: 수집 목적 달성(탈퇴) 시 즉시 파기
        this.name = null;
        this.email = null;
        this.emailHash = null;
        this.phone = null;
        this.phoneHash = null;
        this.gender = null;
        this.birthday = null;
        this.birthYear = null;
        this.ageRange = null;
        this.nickname = null;
        this.kakaoId = null;
        this.naverId = null;
        this.appleId = null;
        this.appleRefreshToken = null;
        this.fcmToken = null;
        this.profileImageUrl = null;
        this.referralMemo = null;
        this.notificationAgreedAt = null;
        this.marketingAgreedAt = null;
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public void updateNotificationConsent(boolean enabled) {
        this.notificationAgreedAt = enabled ? LocalDateTime.now() : null;
    }

    public void updateMarketingConsent(boolean enabled) {
        this.marketingAgreedAt = enabled ? LocalDateTime.now() : null;
    }

    public boolean isNotificationEnabled() {
        return this.notificationAgreedAt != null;
    }

    public boolean isMarketingEnabled() {
        return this.marketingAgreedAt != null;
    }

    public void completeOnboarding() {
        this.onboardingComplete = true;
    }

    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public void updateAppleRefreshToken(String token) {
        this.appleRefreshToken = token;
    }

    public void updateNaverId(String naverId) {
        this.naverId = naverId;
    }

    public void updateAppleId(String appleId) {
        this.appleId = appleId;
    }
}
