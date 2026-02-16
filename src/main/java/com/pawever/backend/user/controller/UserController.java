package com.pawever.backend.user.controller;

import com.pawever.backend.global.common.ApiResponse;
import com.pawever.backend.global.security.UserPrincipal;
import com.pawever.backend.user.dto.UserProfileResponse;
import com.pawever.backend.user.dto.UserUpdateRequest;
import com.pawever.backend.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 내 프로필 조회
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile() {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(userService.getProfile(userId)));
    }

    /**
     * 프로필 정보 수정
     */
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @Valid @RequestBody UserUpdateRequest request) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(userService.updateProfile(userId, request)));
    }

    /**
     * 프로필 사진 업로드/수정
     */
    @PostMapping("/me/profile-image")
    public ResponseEntity<ApiResponse<UserProfileResponse>> uploadProfileImage(
            @RequestParam("file") MultipartFile file) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(userService.updateProfileImage(userId, file)));
    }

    /**
     * 회원 탈퇴
     */
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> withdraw() {
        Long userId = UserPrincipal.getCurrentUserId();
        userService.withdraw(userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
