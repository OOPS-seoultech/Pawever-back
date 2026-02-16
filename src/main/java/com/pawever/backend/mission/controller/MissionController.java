package com.pawever.backend.mission.controller;

import com.pawever.backend.global.common.ApiResponse;
import com.pawever.backend.global.security.UserPrincipal;
import com.pawever.backend.mission.dto.*;
import com.pawever.backend.mission.service.MissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/pets/{petId}")
@RequiredArgsConstructor
public class MissionController {

    private final MissionService missionService;

    /**
     * 발자국 남기기 미션 목록 + 달성 현황 조회 (m/n)
     */
    @GetMapping("/missions")
    public ResponseEntity<ApiResponse<MissionProgressResponse>> getMissionProgress(@PathVariable Long petId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(missionService.getMissionProgress(userId, petId)));
    }

    /**
     * 발자국 남기기 미션 완료 처리
     */
    @PostMapping(value = "/missions/{missionId}/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MissionResponse>> completeMission(
            @PathVariable Long petId,
            @PathVariable Long missionId,
            @RequestPart(required = false) MultipartFile file) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(missionService.completeMission(userId, petId, missionId, file)));
    }

    /**
     * 이별준비 체크리스트 진행률 조회 (%)
     */
    @GetMapping("/checklist")
    public ResponseEntity<ApiResponse<ChecklistProgressResponse>> getChecklistProgress(@PathVariable Long petId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(missionService.getChecklistProgress(userId, petId)));
    }

    /**
     * 체크리스트 항목 토글 (완료/미완료)
     */
    @PostMapping("/checklist/{checklistItemId}/toggle")
    public ResponseEntity<ApiResponse<ChecklistResponse>> toggleChecklistItem(
            @PathVariable Long petId,
            @PathVariable Long checklistItemId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(missionService.toggleChecklistItem(userId, petId, checklistItemId)));
    }
}
