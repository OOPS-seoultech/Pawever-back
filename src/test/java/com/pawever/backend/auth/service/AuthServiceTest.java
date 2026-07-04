package com.pawever.backend.auth.service;

import com.pawever.backend.auth.client.AppleApiClient;
import com.pawever.backend.auth.client.KakaoApiClient;
import com.pawever.backend.auth.client.NaverApiClient;
import com.pawever.backend.auth.dto.NaverLoginRequest;
import com.pawever.backend.auth.dto.TokenResponse;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.global.security.HmacHasher;
import com.pawever.backend.global.security.JwtTokenProvider;
import com.pawever.backend.user.entity.User;
import com.pawever.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 네이버 로그인의 전화번호-해시 매칭이 타 provider 계정을 탈취하지 못하도록 하는 fail-closed 동작 검증.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private KakaoApiClient kakaoApiClient;
    @Mock private NaverApiClient naverApiClient;
    @Mock private AppleApiClient appleApiClient;
    @Mock private HmacHasher hmacHasher;

    @InjectMocks
    private AuthService authService;

    private NaverLoginRequest naverRequest(String token) {
        NaverLoginRequest request = new NaverLoginRequest();
        ReflectionTestUtils.setField(request, "accessToken", token);
        return request;
    }

    private NaverApiClient.NaverUserInfo naverUserInfo(String id, String mobile) {
        NaverApiClient.NaverUserInfo info = new NaverApiClient.NaverUserInfo();
        ReflectionTestUtils.setField(info, "id", id);
        ReflectionTestUtils.setField(info, "mobile", mobile);
        return info;
    }

    @Test
    void naverLogin_whenPhoneMatchesKakaoOnlyAccount_throwsAndDoesNotTakeOver() {
        when(naverApiClient.getUserInfo("t")).thenReturn(naverUserInfo("naver-new", "010-1111-2222"));
        when(userRepository.findByNaverIdAndDeletedAtIsNull("naver-new")).thenReturn(Optional.empty());
        when(hmacHasher.hash("010-1111-2222")).thenReturn("H");
        // 전화번호 해시가 카카오 전용 계정(naverId == null)에 매칭됨
        User kakaoUser = User.builder().id(7L).kakaoId("kakao-1").build();
        when(userRepository.findByPhoneHashAndDeletedAtIsNull("H")).thenReturn(Optional.of(kakaoUser));

        CustomException ex = assertThrows(CustomException.class, () -> authService.naverLogin(naverRequest("t")));

        assertEquals(ErrorCode.DUPLICATE_PHONE_KAKAO, ex.getErrorCode());
        assertNull(kakaoUser.getNaverId());                 // 탈취(덮어쓰기) 발생하지 않음
        verify(jwtTokenProvider, never()).createToken(anyLong()); // 토큰도 발급되지 않음
    }

    @Test
    void naverLogin_whenExistingNaverAccountIdRotated_updatesNaverIdAndLogsIn() {
        when(naverApiClient.getUserInfo("t")).thenReturn(naverUserInfo("naver-new", "010-1111-2222"));
        when(userRepository.findByNaverIdAndDeletedAtIsNull("naver-new")).thenReturn(Optional.empty());
        when(hmacHasher.hash("010-1111-2222")).thenReturn("H");
        // 기존 네이버 계정(naverId 존재)의 id가 바뀐 경우 → 동일인으로 보고 갱신 허용
        User naverUser = User.builder().id(9L).naverId("naver-old").build();
        when(userRepository.findByPhoneHashAndDeletedAtIsNull("H")).thenReturn(Optional.of(naverUser));
        when(jwtTokenProvider.createToken(9L)).thenReturn("jwt");

        TokenResponse response = authService.naverLogin(naverRequest("t"));

        assertFalse(response.isNewUser());
        assertEquals("jwt", response.getAccessToken());
        assertEquals("naver-new", naverUser.getNaverId());  // id 회전 반영됨
    }

    @Test
    void naverLogin_whenNoNaverIdAndNoPhoneMatch_createsNewUser() {
        when(naverApiClient.getUserInfo("t")).thenReturn(naverUserInfo("naver-new", "010-1111-2222"));
        when(userRepository.findByNaverIdAndDeletedAtIsNull("naver-new")).thenReturn(Optional.empty());
        when(hmacHasher.hash("010-1111-2222")).thenReturn("H");
        when(userRepository.findByPhoneHashAndDeletedAtIsNull("H")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenReturn(User.builder().id(1L).build());
        when(jwtTokenProvider.createToken(1L)).thenReturn("jwt");

        TokenResponse response = authService.naverLogin(naverRequest("t"));

        assertTrue(response.isNewUser());
        assertEquals("jwt", response.getAccessToken());
    }
}
