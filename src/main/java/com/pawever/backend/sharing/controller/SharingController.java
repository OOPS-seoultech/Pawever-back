package com.pawever.backend.sharing.controller;

import com.pawever.backend.global.common.ApiResponse;
import com.pawever.backend.global.security.UserPrincipal;
import com.pawever.backend.sharing.dto.*;
import com.pawever.backend.sharing.service.SharingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SharingController {

    private final SharingService sharingService;

    /**
     * 같이 기록하기 멤버 목록 조회
     */
    @GetMapping("/api/pets/{petId}/sharing/members")
    public ResponseEntity<ApiResponse<List<SharedMemberResponse>>> getSharedMembers(@PathVariable Long petId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(sharingService.getSharedMembers(userId, petId)));
    }

    /**
     * 초대코드 조회
     */
    @GetMapping("/api/pets/{petId}/sharing/invite-code")
    public ResponseEntity<ApiResponse<InviteCodeResponse>> getInviteCode(@PathVariable Long petId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(sharingService.getInviteCode(userId, petId)));
    }

    /**
     * 초대코드 재발급
     */
    @PostMapping("/api/pets/{petId}/sharing/invite-code/regenerate")
    public ResponseEntity<ApiResponse<InviteCodeResponse>> regenerateInviteCode(@PathVariable Long petId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(sharingService.regenerateInviteCode(userId, petId)));
    }

    /**
     * 공유 멤버 끊기 (owner 전용)
     */
    @DeleteMapping("/api/pets/{petId}/sharing/members/{targetUserId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable Long petId,
            @PathVariable Long targetUserId) {
        Long userId = UserPrincipal.getCurrentUserId();
        sharingService.removeMember(userId, petId, targetUserId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * 초대코드로 다른 아이 등록하기
     */
    @PostMapping("/api/sharing/join")
    public ResponseEntity<ApiResponse<Void>> joinByInviteCode(@Valid @RequestBody InviteCodeRequest request) {
        Long userId = UserPrincipal.getCurrentUserId();
        sharingService.joinByInviteCode(userId, request.getInviteCode());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
