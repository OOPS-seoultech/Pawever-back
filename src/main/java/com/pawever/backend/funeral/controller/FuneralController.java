package com.pawever.backend.funeral.controller;

import com.pawever.backend.funeral.dto.*;
import com.pawever.backend.funeral.service.FuneralService;
import com.pawever.backend.global.common.ApiResponse;
import com.pawever.backend.global.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/funeral-companies")
@RequiredArgsConstructor
public class FuneralController {

    private final FuneralService funeralService;

    /**
     * 장례업체 목록 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<FuneralCompanyListResponse>>> getFuneralCompanyList() {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(funeralService.getFuneralCompanyList(userId)));
    }

    /**
     * 장례업체 상세 조회
     */
    @GetMapping("/{companyId}")
    public ResponseEntity<ApiResponse<FuneralCompanyResponse>> getFuneralCompanyDetail(
            @PathVariable Long companyId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(funeralService.getFuneralCompanyDetail(userId, companyId)));
    }

    /**
     * 장례업체 저장/피하기 등록
     */
    @PostMapping("/{companyId}/register")
    public ResponseEntity<ApiResponse<Void>> registerFuneralCompany(
            @PathVariable Long companyId,
            @Valid @RequestBody RegisterFuneralCompanyRequest request) {
        Long userId = UserPrincipal.getCurrentUserId();
        funeralService.registerFuneralCompany(userId, companyId, request);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * 장례업체 저장/피하기 해제
     */
    @DeleteMapping("/{companyId}/register")
    public ResponseEntity<ApiResponse<Void>> unregisterFuneralCompany(@PathVariable Long companyId) {
        Long userId = UserPrincipal.getCurrentUserId();
        funeralService.unregisterFuneralCompany(userId, companyId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * 리뷰 작성
     */
    @PostMapping("/{companyId}/reviews")
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @PathVariable Long companyId,
            @Valid @RequestBody ReviewCreateRequest request) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(funeralService.createReview(userId, companyId, request)));
    }

    /**
     * 리뷰 목록 조회
     */
    @GetMapping("/{companyId}/reviews")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getReviews(@PathVariable Long companyId) {
        Long userId = UserPrincipal.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(funeralService.getReviews(userId, companyId)));
    }

    /**
     * 리뷰 삭제
     */
    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(@PathVariable Long reviewId) {
        Long userId = UserPrincipal.getCurrentUserId();
        funeralService.deleteReview(userId, reviewId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
