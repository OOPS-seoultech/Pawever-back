package com.pawever.backend.checklist.controller;

import com.pawever.backend.checklist.dto.ChecklistProgressResponse;
import com.pawever.backend.checklist.dto.ChecklistResponse;
import com.pawever.backend.checklist.service.ChecklistService;
import com.pawever.backend.global.common.ApiResponse;
import com.pawever.backend.global.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Checklist", description = "이별준비 체크리스트 관련 API")
@RestController
@RequestMapping("/api/pets/{petId}")
@RequiredArgsConstructor
public class ChecklistController {

    private final ChecklistService checklistService;

    @Operation(summary = "체크리스트 진행률 조회", description = "이별준비 체크리스트의 진행률(%)을 조회합니다.")
    @GetMapping("/checklist")
    public ResponseEntity<ApiResponse<ChecklistProgressResponse>> getChecklistProgress(@PathVariable Long petId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(checklistService.getChecklistProgress(userId, petId)));
    }

    @Operation(summary = "체크리스트 항목 토글", description = "체크리스트 항목의 완료/미완료 상태를 토글합니다.")
    @PostMapping("/checklist/{checklistItemId}/toggle")
    public ResponseEntity<ApiResponse<ChecklistResponse>> toggleChecklistItem(
            @PathVariable Long petId,
            @PathVariable Long checklistItemId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(checklistService.toggleChecklistItem(userId, petId, checklistItemId)));
    }
}
