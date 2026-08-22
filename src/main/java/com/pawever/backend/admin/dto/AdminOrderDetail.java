package com.pawever.backend.admin.dto;

import com.pawever.backend.goodssurvey.entity.GoodsOrderStatus;

import java.time.Instant;
import java.util.List;

/**
 * 주문 상세.
 *
 * 보호자 이름·연락처·주소는 제작팀에게 비워서 내려간다. 만드는 데 필요 없는
 * 값이라, 볼 수 있게 두면 볼 이유가 없는 것을 보게 된다.
 *
 * @param shipping 제작팀에게는 null
 */
public record AdminOrderDetail(
        String orderNumber,
        Instant submittedAt,
        GoodsOrderStatus status,
        String statusLabel,
        String goodsType,
        String petName,
        Pricing pricing,
        Payment payment,
        Shipping shipping,
        List<Photo> photos,
        Marketing marketing,
        List<StatusChange> statusHistory,
        List<AccessLog> accessLogs
) {

    public record Pricing(
            int listPriceKrw,
            int discountAmountKrw,
            String promotionName,
            int shippingFeeKrw,
            int paymentAmountKrw
    ) {
    }

    public record Payment(
            String method,
            Instant paidAt,
            Instant paymentExpiresAt,
            String cancelReason
    ) {
    }

    public record Shipping(
            String guardianName,
            String phone,
            String postalCode,
            String address,
            String addressDetail,
            String trackingCompany,
            String trackingNumber
    ) {
    }

    /**
     * 사진 한 장.
     *
     * @param slot     1~5. 요구서 3-1 의 사진 1~5 자리다
     * @param objectKey 다운로드 링크를 받을 때 쓰는 값
     * @param filled   비어 있으면 화면에 "미기입" 으로 보여준다
     */
    public record Photo(int slot, String objectKey, boolean filled) {
    }

    public record Marketing(boolean agreed, Instant agreedAt, String version) {
    }

    public record StatusChange(
            String fromStatus,
            String toStatus,
            Instant changedAt,
            String changedBy,
            String memo
    ) {
    }

    public record AccessLog(String action, Long adminAccountId, Instant accessedAt) {
    }
}
