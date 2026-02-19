package com.pawever.backend.memorial.controller;

import com.pawever.backend.global.common.ApiResponse;
import com.pawever.backend.global.security.UserPrincipal;
import com.pawever.backend.memorial.dto.*;
import com.pawever.backend.memorial.service.MemorialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Memorial", description = "추모관 및 이별 가이드 관련 API")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MemorialController {

    private final MemorialService memorialService;

    @Operation(summary = "긴급 대처 모드 활성화", description = "반려동물의 긴급 이별 상황에 대한 가이드를 반환하고 추모관을 생성합니다.")
    @PostMapping("/pets/{petId}/emergency")
    public ResponseEntity<ApiResponse<EmergencyResponse>> activateEmergencyMode(@PathVariable Long petId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(memorialService.activateEmergencyMode(userId, petId)));
    }

    @Operation(summary = "추모관 목록 조회", description = "별자리 추모관 목록을 7일 이내와 이전으로 분리하여 조회합니다.")
    @GetMapping("/memorials")
    public ResponseEntity<ApiResponse<MemorialListResponse>> getMemorialList() {
        return ResponseEntity.ok(ApiResponse.ok(memorialService.getMemorialList()));
    }

    @Operation(summary = "추모관 상세 조회", description = "특정 반려동물의 추모관 상세 정보(펫 정보 + 댓글 목록)를 조회합니다.")
    @GetMapping("/memorials/pet/{petId}")
    public ResponseEntity<ApiResponse<MemorialDetailResponse>> getMemorialDetail(@PathVariable Long petId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(memorialService.getMemorialDetail(userId, petId)));
    }

    @Operation(summary = "추모 댓글 작성", description = "추모관에 댓글을 작성합니다.")
    @PostMapping("/memorials/pet/{petId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @PathVariable Long petId,
            @Valid @RequestBody CommentCreateRequest request) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(memorialService.createComment(userId, petId, request)));
    }

    @Operation(summary = "댓글 수정", description = "작성한 댓글을 수정합니다.")
    @PutMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<CommentResponse>> updateComment(
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(memorialService.updateComment(userId, commentId, request)));
    }

    @Operation(summary = "댓글 삭제", description = "작성한 댓글을 삭제합니다.")
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(@PathVariable Long commentId) {
        Long userId = UserPrincipal.getCurrentUserId();
        memorialService.deleteComment(userId, commentId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "이별 가이드 조회", description = "이별 가이드 데이터 목록을 조회합니다.")
    @GetMapping("/guides")
    public ResponseEntity<ApiResponse<List<GuideResponse>>> getGuides() {
        return ResponseEntity.ok(ApiResponse.ok(memorialService.getGuides()));
    }
}
