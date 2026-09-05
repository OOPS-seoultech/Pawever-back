package com.pawever.backend.admin.controller;

import com.pawever.backend.admin.dto.AdminOrderCancelRequest;
import com.pawever.backend.admin.dto.AdminOrderDetail;
import com.pawever.backend.admin.dto.AdminOrderListResponse;
import com.pawever.backend.admin.dto.AdminOrderStatusRequest;
import com.pawever.backend.admin.dto.AdminOrderTrackingRequest;
import com.pawever.backend.admin.dto.AdminPhotoDownloadResponse;
import com.pawever.backend.admin.security.AdminPrincipal;
import com.pawever.backend.admin.service.AdminOrderService;
import com.pawever.backend.global.common.ApiResponse;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.goodssurvey.entity.GoodsOrderStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 관리자 주문 화면이 부르는 통로.
 *
 * 역할에 따라 무엇이 보이는지는 서비스가 정한다. 여기서는 누가 부른 요청인지만
 * 넘긴다.
 */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminOrderService orderService;

    @GetMapping
    public ApiResponse<AdminOrderListResponse> list(
            @RequestParam(required = false) List<GoodsOrderStatus> status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String goodsType,
            // 한국 날짜다. 담당자가 화면에서 고르는 것은 UTC 자정이 아니다.
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedTo,
            @RequestParam(required = false) Integer minPhotoCount,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Set<GoodsOrderStatus> statuses = status == null || status.isEmpty()
                ? Set.of()
                : EnumSet.copyOf(status);
        return ApiResponse.ok(orderService.list(
                currentPrincipal(),
                statuses,
                q,
                new AdminOrderService.OrderFilter(
                        goodsType, submittedFrom, submittedTo, minPhotoCount),
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE)
        ));
    }

    @GetMapping("/{orderNumber}")
    public ApiResponse<AdminOrderDetail> detail(@PathVariable String orderNumber) {
        return ApiResponse.ok(orderService.detail(currentPrincipal(), orderNumber));
    }

    /** 사진 링크는 잠깐만 열린다. 누가 가져갔는지 이력에 남는다. */
    @PostMapping("/{orderNumber}/photo-links")
    public ApiResponse<AdminPhotoDownloadResponse> photoLinks(@PathVariable String orderNumber) {
        return ApiResponse.ok(orderService.photoLinks(currentPrincipal(), orderNumber));
    }

    /**
     * 사진을 한 번에 내려받는다.
     *
     * 파일이 그대로 나가는 통로라 링크를 내줄 때와 같은 이력을 남긴다.
     */
    @PostMapping("/{orderNumber}/photos.zip")
    public ResponseEntity<byte[]> photoArchive(@PathVariable String orderNumber) {
        AdminOrderService.PhotoArchive archive =
                orderService.photoArchive(currentPrincipal(), orderNumber);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + archive.fileName() + "\"")
                // 고객 사진이 담긴 응답이다. 어디에도 남지 않게 한다.
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(archive.bytes());
    }

    @PostMapping("/{orderNumber}/status")
    public ApiResponse<Void> changeStatus(
            @PathVariable String orderNumber,
            @Valid @RequestBody AdminOrderStatusRequest request
    ) {
        orderService.changeStatus(
                currentPrincipal(), orderNumber, request.status(), request.memo());
        return ApiResponse.ok();
    }

    @PostMapping("/{orderNumber}/tracking")
    public ApiResponse<Void> registerTracking(
            @PathVariable String orderNumber,
            @Valid @RequestBody AdminOrderTrackingRequest request
    ) {
        orderService.registerTracking(
                currentPrincipal(),
                orderNumber,
                request.trackingCompany(),
                request.trackingNumber()
        );
        return ApiResponse.ok();
    }

    /**
     * 현장에서 건넨 주문을 끝낸다.
     *
     * 송장을 받지 않는다. 현장 수령 주문에만 열리고, 그 외 주문은 서비스가
     * 거절한다.
     */
    @PostMapping("/{orderNumber}/pickup-complete")
    public ApiResponse<Void> completePickup(@PathVariable String orderNumber) {
        orderService.completePickup(currentPrincipal(), orderNumber);
        return ApiResponse.ok();
    }

    /**
     * 주문을 취소한다.
     *
     * 결제 취소가 성공해야 취소로 넘어간다. 실패하면 취소 처리 실패로 남고
     * 사람이 확인해야 한다.
     */
    @PostMapping("/{orderNumber}/cancel")
    public ApiResponse<Void> cancel(
            @PathVariable String orderNumber,
            @Valid @RequestBody AdminOrderCancelRequest request
    ) {
        orderService.cancel(currentPrincipal(), orderNumber, request.reason());
        return ApiResponse.ok();
    }

    private AdminPrincipal currentPrincipal() {
        AdminPrincipal principal = AdminPrincipal.current();
        if (principal == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }
}
