package com.pawever.backend.auth;

import com.pawever.backend.auth.dto.*;
import com.pawever.backend.auth.service.AuthService;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.user.entity.User;
import com.pawever.backend.user.repository.UserRepository;
import com.pawever.backend.global.security.JwtTokenProvider;
import com.pawever.backend.global.security.HmacHasher;
import com.pawever.backend.auth.client.KakaoApiClient;
import com.pawever.backend.auth.client.NaverApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private KakaoApiClient kakaoApiClient;

    @Mock
    private NaverApiClient naverApiClient;

    @Mock
    private HmacHasher hmacHasher;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "devLoginPassword", "correct");
    }

    // =========================
    // NAVER LOGIN
    // =========================

    @Test
    void naverLogin_existingUser() {
        String token = "naver-token";

        NaverLoginRequest request = new NaverLoginRequest();
        ReflectionTestUtils.setField(request, "accessToken", token);

        NaverApiClient.NaverUserInfo userInfo = new NaverApiClient.NaverUserInfo();
        ReflectionTestUtils.setField(userInfo, "id", "naverId");

        User user = User.builder().id(1L).build();

        when(naverApiClient.getUserInfo(token)).thenReturn(userInfo);
        when(userRepository.findByNaverIdAndDeletedAtIsNull("naverId")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.createToken(1L)).thenReturn("jwt");

        TokenResponse response = authService.naverLogin(request);

        assertFalse(response.isNewUser());
        assertEquals("jwt", response.getAccessToken());
    }

    @Test
    void naverLogin_newUser() {
        String token = "naver-token";

        NaverLoginRequest request = new NaverLoginRequest();
        ReflectionTestUtils.setField(request, "accessToken", token);

        NaverApiClient.NaverUserInfo userInfo = new NaverApiClient.NaverUserInfo();
        ReflectionTestUtils.setField(userInfo, "id", "naverId");
        ReflectionTestUtils.setField(userInfo, "mobile", "010");

        when(naverApiClient.getUserInfo(token)).thenReturn(userInfo);
        when(userRepository.findByNaverIdAndDeletedAtIsNull("naverId")).thenReturn(Optional.empty());
        when(hmacHasher.hash("010")).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(User.builder().id(1L).build());
        when(jwtTokenProvider.createToken(1L)).thenReturn("jwt");

        TokenResponse response = authService.naverLogin(request);

        assertTrue(response.isNewUser());
        assertEquals("jwt", response.getAccessToken());
    }

    @Test
    void naverLogin_duplicatePhone() {
        String token = "naver-token";

        NaverLoginRequest request = new NaverLoginRequest();
        ReflectionTestUtils.setField(request, "accessToken", token);

        NaverApiClient.NaverUserInfo userInfo = new NaverApiClient.NaverUserInfo();
        ReflectionTestUtils.setField(userInfo, "id", "id");
        ReflectionTestUtils.setField(userInfo, "mobile", "010");

        when(naverApiClient.getUserInfo(token)).thenReturn(userInfo);
        when(userRepository.findByNaverIdAndDeletedAtIsNull("id")).thenReturn(Optional.empty());
        when(hmacHasher.hash("010")).thenReturn("hashed");
        when(userRepository.existsByPhoneHashAndDeletedAtIsNull("hashed")).thenReturn(true);

        assertThrows(CustomException.class, () -> authService.naverLogin(request));
    }

    @Test
    void naverLogin_phoneNull() {
        String token = "naver-token";

        NaverLoginRequest request = new NaverLoginRequest();
        ReflectionTestUtils.setField(request, "accessToken", token);

        NaverApiClient.NaverUserInfo userInfo = new NaverApiClient.NaverUserInfo();
        ReflectionTestUtils.setField(userInfo, "id", "id");

        when(naverApiClient.getUserInfo(token)).thenReturn(userInfo);
        when(userRepository.findByNaverIdAndDeletedAtIsNull("id")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenReturn(User.builder().id(1L).build());
        when(jwtTokenProvider.createToken(1L)).thenReturn("jwt");

        TokenResponse response = authService.naverLogin(request);

        assertTrue(response.isNewUser());
    }

    // =========================
    // KAKAO LOGIN
    // =========================

    @Test
    void kakaoLogin_existingUser() {
        String token = "kakao-token";

        KakaoLoginRequest request = new KakaoLoginRequest();
        ReflectionTestUtils.setField(request, "accessToken", token);

        KakaoApiClient.KakaoUserInfo userInfo = new KakaoApiClient.KakaoUserInfo();
        ReflectionTestUtils.setField(userInfo, "id", 123L);

        User user = User.builder().id(1L).build();

        when(kakaoApiClient.getUserInfo(token)).thenReturn(userInfo);
        when(userRepository.findByKakaoIdAndDeletedAtIsNull(String.valueOf(123L))).thenReturn(Optional.of(user));
        when(jwtTokenProvider.createToken(1L)).thenReturn("jwt");

        TokenResponse response = authService.kakaoLogin(request);

        assertFalse(response.isNewUser());
    }

    @Test
    void kakaoLogin_newUser() {
        String token = "kakao-token";

        KakaoLoginRequest request = new KakaoLoginRequest();
        ReflectionTestUtils.setField(request, "accessToken", token);

        KakaoApiClient.KakaoUserInfo userInfo = new KakaoApiClient.KakaoUserInfo();
        ReflectionTestUtils.setField(userInfo, "id", 123L);

        KakaoApiClient.KakaoUserInfo.KakaoAccount account =
                new KakaoApiClient.KakaoUserInfo.KakaoAccount();
        ReflectionTestUtils.setField(account, "phoneNumber", "010-1234-5678");

        KakaoApiClient.KakaoUserInfo.KakaoAccount.Profile profile =
                new KakaoApiClient.KakaoUserInfo.KakaoAccount.Profile();
        ReflectionTestUtils.setField(profile, "nickname", "nick");
        ReflectionTestUtils.setField(account, "profile", profile);
        ReflectionTestUtils.setField(userInfo, "kakaoAccount", account);

        when(kakaoApiClient.getUserInfo(token)).thenReturn(userInfo);
        when(userRepository.findByKakaoIdAndDeletedAtIsNull(String.valueOf(123L))).thenReturn(Optional.empty());
        when(hmacHasher.hash("010-1234-5678")).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(User.builder().id(1L).build());
        when(jwtTokenProvider.createToken(1L)).thenReturn("jwt");

        TokenResponse response = authService.kakaoLogin(request);

        assertTrue(response.isNewUser());
    }

    // =========================
    // DEV LOGIN
    // =========================

    @Test
    void devLogin_success() {
        DevLoginRequest request = new DevLoginRequest();
        ReflectionTestUtils.setField(request, "password", "correct");

        when(userRepository.save(any())).thenReturn(User.builder().id(1L).build());
        when(jwtTokenProvider.createToken(1L)).thenReturn("jwt");

        TokenResponse response = authService.devLogin(request);

        assertEquals("jwt", response.getAccessToken());
    }

    @Test
    void devLogin_fail() {
        DevLoginRequest request = new DevLoginRequest();
        ReflectionTestUtils.setField(request, "password", "wrong");

        assertThrows(CustomException.class, () -> authService.devLogin(request));
    }
}