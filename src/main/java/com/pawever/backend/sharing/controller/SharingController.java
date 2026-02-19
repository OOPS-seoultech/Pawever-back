package com.pawever.backend.sharing.controller;

import com.pawever.backend.global.common.ApiResponse;
import com.pawever.backend.global.security.UserPrincipal;
import com.pawever.backend.sharing.dto.*;
import com.pawever.backend.sharing.service.SharingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Sharing", description = "같이 기록하기(공유) 관련 API")
@RestController
@RequiredArgsConstructor
public class SharingController {

    private final SharingService sharingService;

    @Operation(summary = "공유 멤버 목록 조회", description = "같이 기록하기에 참여 중인 멤버 목록을 조회합니다.")
    @GetMapping("/api/pets/{petId}/sharing/members")
    public ResponseEntity<ApiResponse<List<SharedMemberResponse>>> getSharedMembers(@PathVariable Long petId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(sharingService.getSharedMembers(userId, petId)));
    }

    @Operation(summary = "초대코드 조회", description = "반려동물 공유를 위한 초대코드를 조회합니다.")
    @GetMapping("/api/pets/{petId}/sharing/invite-code")
    public ResponseEntity<ApiResponse<InviteCodeResponse>> getInviteCode(@PathVariable Long petId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(sharingService.getInviteCode(userId, petId)));
    }

    @Operation(summary = "초대코드 재발급", description = "기존 초대코드를 무효화하고 새로운 초대코드를 발급합니다.")
    @PostMapping("/api/pets/{petId}/sharing/invite-code/regenerate")
    public ResponseEntity<ApiResponse<InviteCodeResponse>> regenerateInviteCode(@PathVariable Long petId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(sharingService.regenerateInviteCode(userId, petId)));
    }

    @Operation(summary = "공유 멤버 제거", description = "공유 멤버를 제거합니다. 반려동물 소유자만 가능합니다.")
    @DeleteMapping("/api/pets/{petId}/sharing/members/{targetUserId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable Long petId,
            @PathVariable Long targetUserId) {
        Long userId = UserPrincipal.getCurrentUserId();
        sharingService.removeMember(userId, petId, targetUserId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "초대코드로 참여", description = "초대코드를 입력하여 다른 사용자의 반려동물 기록에 참여합니다.")
    @PostMapping("/api/sharing/join")
    public ResponseEntity<ApiResponse<Void>> joinByInviteCode(@Valid @RequestBody InviteCodeRequest request) {
        Long userId = UserPrincipal.getCurrentUserId();
        sharingService.joinByInviteCode(userId, request.getInviteCode());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
