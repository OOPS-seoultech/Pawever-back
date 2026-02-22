package com.pawever.backend.auth.service;

import com.pawever.backend.auth.client.KakaoApiClient;
import com.pawever.backend.auth.client.NaverApiClient;
import com.pawever.backend.auth.dto.DevLoginRequest;
import com.pawever.backend.auth.dto.KakaoLoginRequest;
import com.pawever.backend.auth.dto.NaverLoginRequest;
import com.pawever.backend.auth.dto.TokenResponse;
import com.pawever.backend.global.security.JwtTokenProvider;
import com.pawever.backend.user.entity.User;
import com.pawever.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final KakaoApiClient kakaoApiClient;
    private final NaverApiClient naverApiClient;

    @Transactional
    public TokenResponse naverLogin(NaverLoginRequest request) {
        NaverApiClient.NaverUserInfo userInfo = naverApiClient.getUserInfo(request.getAccessToken());

        return userRepository.findByNaverId(userInfo.getId())
                .map(user -> TokenResponse.builder()
                        .accessToken(jwtTokenProvider.createToken(user.getId()))
                        .userId(user.getId())
                        .isNewUser(false)
                        .selectedPetId(user.getSelectedPetId())
                        .build())
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .naverId(userInfo.getId())
                            .nickname(userInfo.getNickname())
                            .name(userInfo.getName())
                            .profileImageUrl(userInfo.getProfileImage())
                            .build();
                    User saved = userRepository.save(newUser);
                    return TokenResponse.builder()
                            .accessToken(jwtTokenProvider.createToken(saved.getId()))
                            .userId(saved.getId())
                            .isNewUser(true)
                            .selectedPetId(null)
                            .build();
                });
    }

    @Transactional
    public TokenResponse kakaoLogin(KakaoLoginRequest request) {
        KakaoApiClient.KakaoUserInfo userInfo = kakaoApiClient.getUserInfo(request.getAccessToken());

        return userRepository.findByKakaoId(userInfo.getKakaoId())
                .map(user -> TokenResponse.builder()
                        .accessToken(jwtTokenProvider.createToken(user.getId()))
                        .userId(user.getId())
                        .isNewUser(false)
                        .selectedPetId(user.getSelectedPetId())
                        .build())
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .kakaoId(userInfo.getKakaoId())
                            .nickname(userInfo.getNickname())
                            .profileImageUrl(userInfo.getProfileImageUrl())
                            .build();
                    User saved = userRepository.save(newUser);
                    return TokenResponse.builder()
                            .accessToken(jwtTokenProvider.createToken(saved.getId()))
                            .userId(saved.getId())
                            .isNewUser(true)
                            .selectedPetId(null)
                            .build();
                });
    }

    /**
     * 개발용 임시 로그인 (소셜 로그인 구현 전까지 사용)
     */
    @Transactional
    public TokenResponse devLogin(DevLoginRequest request) {
        User user = User.builder()
                .name(request.getName())
                .nickname(request.getNickname())
                .build();
        user = userRepository.save(user);

        String token = jwtTokenProvider.createToken(user.getId());

        return TokenResponse.builder()
                .accessToken(token)
                .userId(user.getId())
                .isNewUser(true)
                .build();
    }

    /**
     * 개발용 기존 사용자 로그인
     */
    public TokenResponse devLoginExisting(Long userId) {
        userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new com.pawever.backend.global.exception.CustomException(
                        com.pawever.backend.global.exception.ErrorCode.USER_NOT_FOUND));

        String token = jwtTokenProvider.createToken(userId);

        return TokenResponse.builder()
                .accessToken(token)
                .userId(userId)
                .isNewUser(false)
                .build();
    }
}
