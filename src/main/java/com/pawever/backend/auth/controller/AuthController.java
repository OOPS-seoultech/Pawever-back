package com.pawever.backend.auth.controller;

import com.pawever.backend.auth.dto.DevLoginRequest;
import com.pawever.backend.auth.dto.KakaoLoginRequest;
import com.pawever.backend.auth.dto.NaverLoginRequest;
import com.pawever.backend.auth.dto.TokenResponse;
import com.pawever.backend.auth.service.AuthService;
import com.pawever.backend.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "인증 관련 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "카카오 소셜 로그인", description = "카카오 accessToken으로 로그인 또는 회원가입 후 JWT를 발급합니다.")
    @PostMapping("/login/kakao")
    public ResponseEntity<ApiResponse<TokenResponse>> kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.kakaoLogin(request)));
    }

    @Operation(summary = "네이버 소셜 로그인", description = "네이버 accessToken으로 로그인 또는 회원가입 후 JWT를 발급합니다.")
    @PostMapping("/login/naver")
    public ResponseEntity<ApiResponse<TokenResponse>> naverLogin(@Valid @RequestBody NaverLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.naverLogin(request)));
    }

    @Operation(summary = "개발용 임시 로그인", description = "신규 사용자를 생성하고 JWT 토큰을 발급합니다.")
    @PostMapping("/dev-login")
    public ResponseEntity<ApiResponse<TokenResponse>> devLogin(@RequestBody DevLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.devLogin(request)));
    }

    @Operation(summary = "개발용 기존 사용자 로그인", description = "기존 사용자의 userId로 JWT 토큰을 발급합니다.")
    @PostMapping("/dev-login/{userId}")
    public ResponseEntity<ApiResponse<TokenResponse>> devLoginExisting(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(authService.devLoginExisting(userId)));
    }

//    @Operation(summary = "로그아웃", description = "로그아웃 처리합니다. (토큰 블랙리스트 미구현)")
//    @PostMapping("/logout")
//    public ResponseEntity<ApiResponse<Void>> logout() { // TODO: 토큰 블랙리스트 처리 (Redis 등)
//        return ResponseEntity.ok(ApiResponse.ok());
//    }
}
