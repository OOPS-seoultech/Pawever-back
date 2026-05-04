package com.pawever.backend.auth.service;

import com.pawever.backend.auth.client.AppleApiClient;
import com.pawever.backend.auth.client.KakaoApiClient;
import com.pawever.backend.auth.client.NaverApiClient;
import com.pawever.backend.auth.dto.AppleLoginRequest;
import com.pawever.backend.auth.dto.DevLoginRequest;
import com.pawever.backend.auth.dto.KakaoLoginRequest;
import com.pawever.backend.auth.dto.NaverLoginRequest;
import com.pawever.backend.auth.dto.TokenResponse;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.global.security.HmacHasher;
import com.pawever.backend.global.security.JwtTokenProvider;
import com.pawever.backend.global.util.UrlUtils;
import com.pawever.backend.user.entity.User;
import com.pawever.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final KakaoApiClient kakaoApiClient;
    private final NaverApiClient naverApiClient;
    private final AppleApiClient appleApiClient;
    private final HmacHasher hmacHasher;

    @Value("${dev.login.password:}")
    private String devLoginPassword;

    @Transactional
    public TokenResponse naverLogin(NaverLoginRequest request) {
        NaverApiClient.NaverUserInfo userInfo = naverApiClient.getUserInfo(request.getAccessToken());

        // 1. naverId로 기존 유저 조회
        var byNaverId = userRepository.findByNaverIdAndDeletedAtIsNull(userInfo.getId());
        if (byNaverId.isPresent()) {
            User user = byNaverId.get();
            return TokenResponse.builder()
                    .accessToken(jwtTokenProvider.createToken(user.getId()))
                    .userId(user.getId())
                    .isNewUser(false)
                    .selectedPetId(user.getSelectedPetId())
                    .onboardingComplete(user.isOnboardingComplete())
                    .build();
        }

        // 2. naverId 불일치 → 전화번호 해시로 동일인 확인
        String phoneHash = hashPhone(userInfo.getMobile());
        if (phoneHash != null) {
            var byPhone = userRepository.findByPhoneHashAndDeletedAtIsNull(phoneHash);
            if (byPhone.isPresent()) {
                User user = byPhone.get();
                log.info("[NaverLogin] naverId 변경 감지: userId={} old={} new={}", user.getId(), user.getNaverId(), userInfo.getId());
                user.updateNaverId(userInfo.getId());
                return TokenResponse.builder()
                        .accessToken(jwtTokenProvider.createToken(user.getId()))
                        .userId(user.getId())
                        .isNewUser(false)
                        .selectedPetId(user.getSelectedPetId())
                        .onboardingComplete(user.isOnboardingComplete())
                        .build();
            }
        }

        // 3. 신규 유저 생성 (전화번호 중복은 이미 위에서 처리됨)
        User newUser = User.builder()
                .naverId(userInfo.getId())
                .nickname(userInfo.getNickname())
                .name(userInfo.getName())
                .email(userInfo.getEmail())
                .phone(userInfo.getMobile())
                .phoneHash(phoneHash)
                .gender(userInfo.getGender())
                .birthday(userInfo.getBirthday())
                .birthYear(userInfo.getBirthyear())
                .ageRange(userInfo.getAge())
                .profileImageUrl(UrlUtils.toHttpsUrl(userInfo.getProfileImage()))
                .build();
        User saved = userRepository.save(newUser);
        return TokenResponse.builder()
                .accessToken(jwtTokenProvider.createToken(saved.getId()))
                .userId(saved.getId())
                .isNewUser(true)
                .selectedPetId(null)
                .onboardingComplete(saved.isOnboardingComplete())
                .build();
    }

    @Transactional
    public TokenResponse kakaoLogin(KakaoLoginRequest request) {
        KakaoApiClient.KakaoUserInfo userInfo = kakaoApiClient.getUserInfo(request.getAccessToken());

        return userRepository.findByKakaoIdAndDeletedAtIsNull(userInfo.getKakaoId())
                .map(user -> TokenResponse.builder()
                        .accessToken(jwtTokenProvider.createToken(user.getId()))
                        .userId(user.getId())
                        .isNewUser(false)
                        .selectedPetId(user.getSelectedPetId())
                        .onboardingComplete(user.isOnboardingComplete())
                        .build())
                .orElseGet(() -> {
                    String phoneHash = hashPhone(userInfo.getPhone());
                    checkDuplicatePhone(phoneHash);

                    User newUser = User.builder()
                            .kakaoId(userInfo.getKakaoId())
                            .nickname(userInfo.getNickname())
                            .name(userInfo.getName())
                            .email(userInfo.getEmail())
                            .phone(userInfo.getPhone())
                            .phoneHash(phoneHash)
                            .gender(userInfo.getGender())
                            .birthday(userInfo.getBirthday())
                            .birthYear(userInfo.getBirthYear())
                            .ageRange(userInfo.getAgeRange())
                            .profileImageUrl(UrlUtils.toHttpsUrl(userInfo.getProfileImageUrl()))
                            .build();
                    User saved = userRepository.save(newUser);
                    return TokenResponse.builder()
                            .accessToken(jwtTokenProvider.createToken(saved.getId()))
                            .userId(saved.getId())
                            .isNewUser(true)
                            .selectedPetId(null)
                            .onboardingComplete(saved.isOnboardingComplete())
                            .build();
                });
    }

    @Transactional
    public TokenResponse appleLogin(AppleLoginRequest request) {
        AppleApiClient.AppleUserInfo userInfo = appleApiClient.getUserInfo(request.getIdentityToken());

        boolean isNewUser;
        User user;

        // 1. appleId로 기존 유저 조회
        var byAppleId = userRepository.findByAppleIdAndDeletedAtIsNull(userInfo.appleId());
        if (byAppleId.isPresent()) {
            user = byAppleId.get();
            isNewUser = false;
        } else {
            // 2. 이메일 해시로 동일인 확인 (appleId가 바뀐 경우)
            String emailHash = hashEmail(userInfo.email());
            var byEmail = emailHash != null ? userRepository.findByEmailHashAndDeletedAtIsNull(emailHash) : java.util.Optional.<User>empty();
            if (byEmail.isPresent()) {
                user = byEmail.get();
                log.info("[AppleLogin] appleId 변경 감지: userId={} new appleId={}", user.getId(), userInfo.appleId());
                user.updateAppleId(userInfo.appleId());
                isNewUser = false;
            } else {
                user = userRepository.save(User.builder()
                        .appleId(userInfo.appleId())
                        .email(userInfo.email())
                        .emailHash(emailHash)
                        .build());
                isNewUser = true;
            }
        }

        // authorizationCode가 있으면 refresh_token 교환하여 저장 (탈퇴 시 취소용)
        if (StringUtils.hasText(request.getAuthorizationCode())) {
            try {
                String refreshToken = appleApiClient.exchangeAuthCode(request.getAuthorizationCode());
                user.updateAppleRefreshToken(refreshToken);
            } catch (Exception e) {
                log.warn("Apple auth code exchange failed for user {}: {}", user.getId(), e.getMessage());
            }
        }

        return TokenResponse.builder()
                .accessToken(jwtTokenProvider.createToken(user.getId()))
                .userId(user.getId())
                .isNewUser(isNewUser)
                .selectedPetId(user.getSelectedPetId())
                .onboardingComplete(user.isOnboardingComplete())
                .build();
    }

    /**
     * 개발용 로그인 (비밀번호 검증 후 테스트 유저 생성 및 JWT 발급)
     */
    @Transactional
    public TokenResponse devLogin(DevLoginRequest request) {
        if (devLoginPassword.isBlank() || !devLoginPassword.equals(request.getPassword())) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        User user = userRepository.save(User.builder().build());

        return TokenResponse.builder()
                .accessToken(jwtTokenProvider.createToken(user.getId()))
                .userId(user.getId())
                .isNewUser(true)
                .onboardingComplete(user.isOnboardingComplete())
                .build();
    }

    private String hashPhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        return hmacHasher.hash(phone);
    }

    private String hashEmail(String email) {
        if (email == null || email.isBlank()) return null;
        return hmacHasher.hash(email);
    }

    private void checkDuplicatePhone(String phoneHash) {
        if (phoneHash == null) return;
        userRepository.findByPhoneHashAndDeletedAtIsNull(phoneHash).ifPresent(existing -> {
            if (existing.getKakaoId() != null) {
                throw new CustomException(ErrorCode.DUPLICATE_PHONE_KAKAO);
            } else if (existing.getNaverId() != null) {
                throw new CustomException(ErrorCode.DUPLICATE_PHONE_NAVER);
            } else if (existing.getAppleId() != null) {
                throw new CustomException(ErrorCode.DUPLICATE_PHONE_APPLE);
            } else {
                throw new CustomException(ErrorCode.DUPLICATE_PHONE);
            }
        });
    }
}
