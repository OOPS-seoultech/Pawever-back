package com.pawever.backend.auth.service;

import com.pawever.backend.auth.dto.DevLoginRequest;
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

    /**
     * TODO: 카카오 소셜 로그인 구현
     * 1. 카카오 accessToken으로 카카오 API 호출하여 사용자 정보 조회
     * 2. kakaoId로 기존 회원 조회 또는 신규 생성
     * 3. JWT 토큰 발급
     */

    /**
     * TODO: 네이버 소셜 로그인 구현
     * 1. 네이버 accessToken으로 네이버 API 호출하여 사용자 정보 조회
     * 2. naverId로 기존 회원 조회 또는 신규 생성
     * 3. JWT 토큰 발급
     */

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
