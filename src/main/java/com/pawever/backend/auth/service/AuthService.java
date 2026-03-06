package com.pawever.backend.auth.service;

import com.pawever.backend.auth.client.KakaoApiClient;
import com.pawever.backend.auth.client.NaverApiClient;
import com.pawever.backend.auth.dto.DevLoginRequest;
import com.pawever.backend.auth.dto.KakaoLoginRequest;
import com.pawever.backend.auth.dto.NaverLoginRequest;
import com.pawever.backend.auth.dto.TokenResponse;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.global.security.HmacHasher;
import com.pawever.backend.global.security.JwtTokenProvider;
import com.pawever.backend.user.entity.User;
import com.pawever.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
    private final HmacHasher hmacHasher;

    @Value("${dev.login.password:}")
    private String devLoginPassword;

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
                    String phoneHash = hashPhone(userInfo.getMobile());
                    checkDuplicatePhone(phoneHash);

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
                .build();
    }

    private String hashPhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        return hmacHasher.hash(phone);
    }

    private void checkDuplicatePhone(String phoneHash) {
        if (phoneHash == null) return;
        if (userRepository.existsByPhoneHashAndDeletedAtIsNull(phoneHash)) {
            throw new CustomException(ErrorCode.DUPLICATE_PHONE);
        }
    }
}
