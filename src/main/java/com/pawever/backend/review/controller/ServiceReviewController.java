package com.pawever.backend.review.controller;

import com.pawever.backend.global.common.ApiResponse;
import com.pawever.backend.global.security.UserPrincipal;
import com.pawever.backend.review.service.ServiceReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "ServiceReview", description = "서비스 후기 API")
@RestController
@RequestMapping("/api/service-reviews")
@RequiredArgsConstructor
public class ServiceReviewController {

    private final ServiceReviewService serviceReviewService;

    @Operation(summary = "서비스 후기 작성", description = "Pawever 서비스 후기를 작성합니다. 텍스트와 이미지를 함께 전송할 수 있습니다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> createServiceReview(
            @RequestPart(value = "content", required = false) String content,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        Long userId = UserPrincipal.getCurrentUserId();
        serviceReviewService.createServiceReview(userId, content, images);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
