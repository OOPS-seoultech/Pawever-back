package com.pawever.backend.funeral.controller;

import com.pawever.backend.funeral.dto.*;
import com.pawever.backend.funeral.service.FuneralService;
import com.pawever.backend.global.common.ApiResponse;
import com.pawever.backend.global.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Funeral", description = "장례업체 관련 API")
@RestController
@RequestMapping("/api/funeral-companies")
@RequiredArgsConstructor
public class FuneralController {

    private final FuneralService funeralService;

    @Operation(summary = "장례업체 목록 조회", description = "장례업체 전체 목록을 거리순으로 조회합니다. 위치 미제공 시 서울역 기준으로 정렬됩니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<FuneralCompanyListResponse>>> getFuneralCompanyList(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(funeralService.getFuneralCompanyList(userId, latitude, longitude)));
    }

    @Operation(summary = "장례업체 상세 조회", description = "특정 장례업체의 상세 정보를 조회합니다.")
    @GetMapping("/{companyId}")
    public ResponseEntity<ApiResponse<FuneralCompanyResponse>> getFuneralCompanyDetail(
            @PathVariable Long companyId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(funeralService.getFuneralCompanyDetail(userId, companyId)));
    }

    @Operation(summary = "장례업체 저장/피하기 등록", description = "장례업체를 저장하거나 피하기로 등록합니다.")
    @PostMapping("/{companyId}/register")
    public ResponseEntity<ApiResponse<Void>> registerFuneralCompany(
            @PathVariable Long companyId,
            @Valid @RequestBody RegisterFuneralCompanyRequest request) {
        Long userId = UserPrincipal.getCurrentUserId();
        funeralService.registerFuneralCompany(userId, companyId, request);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "장례업체 저장/피하기 해제", description = "장례업체의 저장 또는 피하기 등록을 해제합니다.")
    @DeleteMapping("/{companyId}/register")
    public ResponseEntity<ApiResponse<Void>> unregisterFuneralCompany(@PathVariable Long companyId) {
        Long userId = UserPrincipal.getCurrentUserId();
        funeralService.unregisterFuneralCompany(userId, companyId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "리뷰 작성", description = "장례업체에 리뷰를 작성합니다.")
    @PostMapping("/{companyId}/reviews")
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @PathVariable Long companyId,
            @Valid @RequestBody ReviewCreateRequest request) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(funeralService.createReview(userId, companyId, request)));
    }

    @Operation(summary = "리뷰 목록 조회", description = "특정 장례업체의 리뷰 목록을 조회합니다.")
    @GetMapping("/{companyId}/reviews")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getReviews(@PathVariable Long companyId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(funeralService.getReviews(userId, companyId)));
    }

    @Operation(summary = "리뷰 삭제", description = "작성한 리뷰를 삭제합니다.")
    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(@PathVariable Long reviewId) {
        Long userId = UserPrincipal.getCurrentUserId();
        funeralService.deleteReview(userId, reviewId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
