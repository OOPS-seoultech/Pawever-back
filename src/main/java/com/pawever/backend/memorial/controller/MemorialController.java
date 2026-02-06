package com.pawever.backend.memorial.controller;

import com.pawever.backend.global.common.ApiResponse;
import com.pawever.backend.global.security.UserPrincipal;
import com.pawever.backend.memorial.dto.*;
import com.pawever.backend.memorial.service.MemorialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MemorialController {

    private final MemorialService memorialService;

    /**
     * 긴급 대처 모드 - Memorial 생성 + 이별 가이드 반환
     */
    @PostMapping("/pets/{petId}/emergency")
    public ResponseEntity<ApiResponse<EmergencyResponse>> activateEmergencyMode(@PathVariable Long petId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(memorialService.activateEmergencyMode(userId, petId)));
    }

    /**
     * 별자리 추모관 목록 조회 (7일 이내 / 이전 분리)
     */
    @GetMapping("/memorials")
    public ResponseEntity<ApiResponse<MemorialListResponse>> getMemorialList() {
        return ResponseEntity.ok(ApiResponse.ok(memorialService.getMemorialList()));
    }

    /**
     * 별자리 상세 조회 (펫 정보 + 댓글 목록)
     */
    @GetMapping("/memorials/pet/{petId}")
    public ResponseEntity<ApiResponse<MemorialDetailResponse>> getMemorialDetail(@PathVariable Long petId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(memorialService.getMemorialDetail(userId, petId)));
    }

    /**
     * 댓글 작성
     */
    @PostMapping("/memorials/{memorialId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @PathVariable Long memorialId,
            @Valid @RequestBody CommentCreateRequest request) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(memorialService.createComment(userId, memorialId, request)));
    }

    /**
     * 댓글 수정
     */
    @PutMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<CommentResponse>> updateComment(
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(memorialService.updateComment(userId, commentId, request)));
    }

    /**
     * 댓글 삭제
     */
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(@PathVariable Long commentId) {
        Long userId = UserPrincipal.getCurrentUserId();
        memorialService.deleteComment(userId, commentId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * 이별 가이드 데이터 조회
     */
    @GetMapping("/guides")
    public ResponseEntity<ApiResponse<List<GuideResponse>>> getGuides() {
        return ResponseEntity.ok(ApiResponse.ok(memorialService.getGuides()));
    }
}
