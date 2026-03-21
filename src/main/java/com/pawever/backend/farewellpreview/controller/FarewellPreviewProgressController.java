package com.pawever.backend.farewellpreview.controller;

import com.pawever.backend.farewellpreview.dto.FarewellPreviewProgressResponse;
import com.pawever.backend.farewellpreview.dto.FarewellPreviewProgressUpdateRequest;
import com.pawever.backend.farewellpreview.service.FarewellPreviewProgressService;
import com.pawever.backend.global.common.ApiResponse;
import com.pawever.backend.global.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "FarewellPreview", description = "미리 살펴보기 진행 상태 관련 API")
@RestController
@RequestMapping("/api/pets/{petId}")
@RequiredArgsConstructor
public class FarewellPreviewProgressController {

    private final FarewellPreviewProgressService farewellPreviewProgressService;

    @Operation(summary = "미리 살펴보기 진행 상태 조회", description = "미리 살펴보기 복원에 필요한 세부 진행 상태와 퍼센트를 조회합니다.")
    @GetMapping("/farewell-preview-progress")
    public ResponseEntity<ApiResponse<FarewellPreviewProgressResponse>> getFarewellPreviewProgress(
            @PathVariable Long petId
    ) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(
                farewellPreviewProgressService.getFarewellPreviewProgress(userId, petId)
        ));
    }

    @Operation(summary = "미리 살펴보기 진행 상태 저장", description = "오너가 미리 살펴보기 세부 진행 상태 전체 스냅샷을 저장합니다.")
    @PutMapping("/farewell-preview-progress")
    public ResponseEntity<ApiResponse<FarewellPreviewProgressResponse>> updateFarewellPreviewProgress(
            @PathVariable Long petId,
            @RequestBody FarewellPreviewProgressUpdateRequest request
    ) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(
                farewellPreviewProgressService.updateFarewellPreviewProgress(userId, petId, request)
        ));
    }
}
