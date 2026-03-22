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

@Tag(name = "Mission", description = "발자국 남기기 미션 관련 API")
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

    @Operation(summary = "미션 완료 처리", description = "발자국 남기기 미션을 완료 처리합니다. 인증 파일을 첨부할 수 있습니다.")
    @PostMapping(value = "/missions/{missionId}/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MissionResponse>> completeMission(
            @PathVariable Long petId,
            @PathVariable Long missionId,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(missionService.completeMission(userId, petId, missionId, file)));
    }

    @Operation(summary = "미션 녹음 저장", description = "발자국 남기기 녹음/마음전하기 미션의 오디오 파일과 메타데이터를 저장합니다.")
    @PostMapping(value = "/missions/{missionId}/recording", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MissionResponse>> saveMissionRecording(
            @PathVariable Long petId,
            @PathVariable Long missionId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "durationSec", required = false) Integer durationSec,
            @RequestParam(value = "format", required = false) String format,
            @RequestParam(value = "sizeBytes", required = false) Long sizeBytes,
            @RequestParam(value = "waveform", required = false) String waveform) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(
                missionService.saveMissionRecording(userId, petId, missionId, file, durationSec, format, sizeBytes, waveform)
        ));
    }

    @Operation(summary = "홈화면 진행률 요약 조회", description = "미리 살펴보기 진행률(%)과 미션 완료/전체 수를 조회합니다.")
    @GetMapping("/home-progress")
    public ResponseEntity<ApiResponse<HomeProgressResponse>> getHomeProgress(@PathVariable Long petId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(missionService.getHomeProgress(userId, petId)));
    }
}
