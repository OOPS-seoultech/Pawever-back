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
        /** 사람이 읽는 굿즈 이름. */
        String goodsTypeLabel,
        /** 첫 아이 이름. 목록·문자처럼 한 줄만 쓰는 자리가 본다. */
        String petName,
        /**
         * 이 주문에 담긴 아이들.
         *
         * 한 주문에 여러 마리가 올 수 있다. 첫 아이 이름만 보고 한 마리로
         * 여기면 만들다 빠뜨린다.
         */
        List<Pet> pets,
        Pricing pricing,
        Payment payment,
        Shipping shipping,
        List<Photo> photos,
        Marketing marketing,
        List<StatusChange> statusHistory,
        List<AccessLog> accessLogs
) {

    /**
     * 주문에 담긴 아이 한 마리.
     *
     * @param photoCount        제출 시점의 사진 장수
     * @param lowPhotoAcknowledged 사진이 적다는 안내를 확인하고 낸 것인지
     */
    public record Pet(
            int index,
            String petName,
            boolean keyringAdded,
            int photoCount,
            boolean lowPhotoAcknowledged
    ) {
    }

    public record Pricing(
            int listPriceKrw,
            int discountAmountKrw,
            String promotionName,
            int shippingFeeKrw,
            int keyringFeeKrw,
            int paymentAmountKrw
    ) {
    }

    /**
     * @param pgLinked 결제 대행사 결제 키가 붙어 있는지. 붙어 있으면 취소가 대행사
     *                 환불까지 함께 하고, 없으면(계좌이체) 환불은 사람이 먼저 한다.
     *                 화면이 어느 쪽 말을 할지가 여기서 갈린다.
     */
    public record Payment(
            String method,
            Instant paidAt,
            Instant paymentExpiresAt,
            String cancelReason,
            boolean pgLinked
    ) {
    }

    /**
     * @param deliveryMethod SHIPPING 이면 부치고 PICKUP 이면 행사장에서 건넨다.
     *                       PICKUP 은 주소가 비어 있다.
     */
    public record Shipping(
            String guardianName,
            String phone,
            String deliveryMethod,
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
