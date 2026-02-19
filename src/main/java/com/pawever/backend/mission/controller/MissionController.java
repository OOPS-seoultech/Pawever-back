package com.pawever.backend.mission.controller;

import com.pawever.backend.global.common.ApiResponse;
import com.pawever.backend.global.security.UserPrincipal;
import com.pawever.backend.mission.dto.*;
import com.pawever.backend.mission.service.MissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Mission", description = "미션 및 체크리스트 관련 API")
@RestController
@RequestMapping("/api/pets/{petId}")
@RequiredArgsConstructor
public class MissionController {

    private final MissionService missionService;

    @Operation(summary = "미션 진행 현황 조회", description = "발자국 남기기 미션 목록과 달성 현황(m/n)을 조회합니다.")
    @GetMapping("/missions")
    public ResponseEntity<ApiResponse<MissionProgressResponse>> getMissionProgress(@PathVariable Long petId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(missionService.getMissionProgress(userId, petId)));
    }

    @Operation(summary = "미션 완료 처리", description = "발자국 남기기 미션을 완료 처리합니다. 인증 사진을 첨부할 수 있습니다.")
    @PostMapping(value = "/missions/{missionId}/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MissionResponse>> completeMission(
            @PathVariable Long petId,
            @PathVariable Long missionId,
            @RequestPart(required = false) MultipartFile file) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(missionService.completeMission(userId, petId, missionId, file)));
    }

    @Operation(summary = "체크리스트 진행률 조회", description = "이별준비 체크리스트의 진행률(%)을 조회합니다.")
    @GetMapping("/checklist")
    public ResponseEntity<ApiResponse<ChecklistProgressResponse>> getChecklistProgress(@PathVariable Long petId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(missionService.getChecklistProgress(userId, petId)));
    }

    @Operation(summary = "체크리스트 항목 토글", description = "체크리스트 항목의 완료/미완료 상태를 토글합니다.")
    @PostMapping("/checklist/{checklistItemId}/toggle")
    public ResponseEntity<ApiResponse<ChecklistResponse>> toggleChecklistItem(
            @PathVariable Long petId,
            @PathVariable Long checklistItemId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(missionService.toggleChecklistItem(userId, petId, checklistItemId)));
    }
}
