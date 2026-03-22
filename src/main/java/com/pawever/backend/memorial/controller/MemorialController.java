package com.pawever.backend.memorial.controller;

import com.pawever.backend.global.common.ApiResponse;
import com.pawever.backend.global.security.UserPrincipal;
import com.pawever.backend.memorial.dto.*;
import com.pawever.backend.pet.dto.PetResponse;
import com.pawever.backend.memorial.service.MemorialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "Memorial", description = "추모관 관련 API")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MemorialController {

    private final MemorialService memorialService;

    @Operation(summary = "긴급 대처 모드 활성화", description = "반려동물을 이별 후 상태로 전환하고 추모관(메모리얼) 정보를 반환합니다.")
    @PostMapping("/pets/{petId}/emergency")
    public ResponseEntity<ApiResponse<EmergencyResponse>> activateEmergencyMode(@PathVariable Long petId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(memorialService.activateEmergencyMode(userId, petId)));
    }

    @Operation(summary = "긴급 대처 모드 완료", description = "반려동물을 이별 후 상태로 확정하고 긴급 대처 모드를 종료합니다. 장례업체 저장/피하기와 미리 살펴보기 진행 상태는 초기화됩니다.")
    @PostMapping("/pets/{petId}/emergency/complete")
    public ResponseEntity<ApiResponse<PetResponse>> completeEmergencyMode(@PathVariable Long petId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(memorialService.completeEmergencyMode(userId, petId)));
    }

    @Operation(summary = "긴급 대처 모드 해제", description = "반려동물을 이별 전 상태로 되돌리고 긴급 대처 모드를 해제합니다. 장례업체 저장/피하기는 초기화되지만 미리 살펴보기 진행 상태는 유지합니다.")
    @PostMapping("/pets/{petId}/emergency/deactivate")
    public ResponseEntity<ApiResponse<PetResponse>> deactivateEmergencyMode(@PathVariable Long petId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(memorialService.deactivateEmergencyMode(userId, petId)));
    }

    @Operation(summary = "이별 후에서 이별 전으로 복귀", description = "반려동물을 이별 후 상태에서 이별 전 상태로 되돌립니다. 추모 댓글은 유지되며 장례업체 저장/피하기와 미리 살펴보기 진행 상태는 초기화됩니다.")
    @PostMapping("/pets/{petId}/farewell/revert")
    public ResponseEntity<ApiResponse<PetResponse>> revertFarewell(@PathVariable Long petId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(memorialService.revertFarewell(userId, petId)));
    }

    @Operation(summary = "추모관 목록 조회", description = "별자리 추모관 feed를 recent/past 버퍼 단위 cursor pagination으로 조회합니다.")
    @GetMapping("/memorials")
    public ResponseEntity<ApiResponse<MemorialFeedResponse>> getMemorialList(
            @RequestParam(required = false) Integer recentSize,
            @RequestParam(required = false) Integer pastSize,
            @RequestParam(required = false) String recentCursor,
            @RequestParam(required = false) String pastCursor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime referenceTime
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                memorialService.getMemorialFeed(recentSize, pastSize, recentCursor, pastCursor, referenceTime)
        ));
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

    @Operation(summary = "댓글 신고 사유 목록", description = "추모관 댓글 신고 시 선택할 수 있는 사유 목록을 조회합니다.")
    @GetMapping("/report-reasons")
    public ResponseEntity<ApiResponse<List<ReportReasonResponse>>> getReportReasons() {
        return ResponseEntity.ok(ApiResponse.ok(memorialService.getReportReasons()));
    }

    @Operation(summary = "댓글 신고", description = "추모관 댓글을 신고합니다. 사유를 여러 개 선택하거나, 해당 없으면 직접 입력할 수 있습니다.")
    @PostMapping("/comments/{commentId}/report")
    public ResponseEntity<ApiResponse<Void>> reportComment(
            @PathVariable Long commentId,
            @Valid @RequestBody CommentReportRequest request) {
        Long userId = UserPrincipal.getCurrentUserId();
        memorialService.reportComment(userId, commentId, request);
        return ResponseEntity.ok(ApiResponse.ok());
    }

}
