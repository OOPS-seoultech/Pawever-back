package com.pawever.backend.auth.controller;

import com.pawever.backend.auth.dto.DevLoginRequest;
import com.pawever.backend.auth.dto.TokenResponse;
import com.pawever.backend.auth.service.AuthService;
import com.pawever.backend.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * TODO: 카카오 소셜 로그인
     * POST /api/auth/login/kakao
     */

    /**
     * TODO: 네이버 소셜 로그인
     * POST /api/auth/login/naver
     */

    /**
     * 개발용 임시 로그인 - 신규 사용자 생성 후 토큰 발급
     */
    @PostMapping("/dev-login")
    public ResponseEntity<ApiResponse<TokenResponse>> devLogin(@RequestBody DevLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.devLogin(request)));
    }

    /**
     * 개발용 기존 사용자 로그인
     */
    @PostMapping("/dev-login/{userId}")
    public ResponseEntity<ApiResponse<TokenResponse>> devLoginExisting(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(authService.devLoginExisting(userId)));
    }

    /**
     * TODO: 로그아웃 (토큰 블랙리스트 처리)
     * JWT 기반이므로 클라이언트에서 토큰 삭제로 처리 가능
     * 서버 측 블랙리스트가 필요하면 Redis 등 활용
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        // TODO: 토큰 블랙리스트 처리 (Redis 등)
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
