package com.pawever.backend.admin.dto;

import com.pawever.backend.goodssurvey.entity.GoodsOrderStatus;

import java.time.Instant;

/**
 * 주문 목록 한 줄.
 *
 * 연락처와 주소는 가려서 담는다. 목록을 훑어보는 데 전체 값이 필요하지 않고,
 * 화면을 켜 두는 것만으로 어깨너머와 화면 공유에 흘러간다.
 *
 * @param photoCount 올린 사진 수. 화면에서 "3/5" 로 보여준다
 */
public record AdminOrderSummary(
        String orderNumber,
        Instant submittedAt,
        String goodsType,
        /** 사람이 읽는 굿즈 이름. 코드값만 보면 무엇인지 알 수 없다. */
        String goodsTypeLabel,
        String petName,
        String guardianNameMasked,
        String phoneMasked,
        GoodsOrderStatus status,
        String statusLabel,
        int photoCount,
        int paymentAmountKrw,
        Instant paidAt,
        /**
         * 부칠 건인지 넘겨줄 건인지. SHIPPING 또는 PICKUP.
         *
         * 목록에서 갈라 보여야 한다. 상세를 하나씩 열어 확인하면 스무 건을
         * 포장하는 동안 한 건은 반드시 섞인다.
         */
        String deliveryMethod,
        String trackingNumber
) {
}
