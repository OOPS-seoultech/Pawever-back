package com.pawever.backend.goodssurvey.dto;

import tools.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SubmitGoodsSurveyApplicationRequest(
        @NotBlank @Size(max = 30) String goodsType,
        @Size(max = 300) String customGoods,
        /**
         * 예전 화면이 보내던 아이 이름 한 개.
         *
         * {@link #pets()} 가 있으면 쓰지 않는다. 화면과 서버는 따로 배포되므로
         * 서버가 먼저 올라간 동안에도 예전 화면의 신청을 받아야 한다.
         */
        @Size(max = 50) String petName,
        @NotBlank @Size(max = 50) String guardianName,
        @NotBlank
        @Pattern(regexp = "^(?:\\+?82)?0?1[016789][0-9]{7,8}$|^01[016789]-?[0-9]{3,4}-?[0-9]{4}$")
        String phone,
        /**
         * 건네는 방법. SHIPPING 또는 PICKUP 이고, 비어 있으면 택배로 본다.
         *
         * PICKUP 은 행사장이 있는 경로에서만 고를 수 있다. 이때 주소는 받지
         * 않고 배송비도 붙지 않는다.
         */
        @Size(max = 20) String deliveryMethod,
        // 택배일 때만 있어야 한다. 현장 수령은 받는 사람이 그 자리에 온다.
        @Size(max = 10) String postalCode,
        @Size(max = 200) String address,
        @Size(max = 200) String addressDetail,
        /** 예전 화면이 보내던 사진 묶음. {@link #pets()} 가 있으면 쓰지 않는다. */
        @Size(max = 5) List<@NotBlank @Size(max = 36) String> photoIds,
        @Size(max = 5) List<@NotBlank @Size(max = 36) String> publicPhotoIds,
        @NotBlank @Size(max = 80) String conversionEventId,
        @NotNull JsonNode tracking,
        /** 예전 화면이 보내던 키링 여부. {@link #pets()} 가 있으면 쓰지 않는다. */
        boolean keyringAdded,
        @AssertTrue boolean privacyAgreed,
        @AssertTrue boolean shippingConfirmed,
        /**
         * 광고성 정보 수신 동의.
         *
         * 개인정보 수집·이용 동의와 달리 강제하지 않는다. 굿즈를 사는 데 필요한
         * 동의와 묶으면 사실상 거부할 수 없게 되어 별도 동의가 아니게 된다.
         */
        boolean marketingAgreed,
        /**
         * 한 신청에 담은 아이들.
         *
         * 아이마다 이름·사진·키링을 따로 받는다. 한 주문에 두 마리를 넣고
         * 한 마리 값만 받던 일을 막는 것이 이 항목의 목적이다. 값이 비어 있으면
         * 예전 화면이 보낸 것으로 보고 위의 낱개 항목을 한 마리로 읽는다.
         */
        @Size(max = 5) List<@Valid Pet> pets
) {

    /**
     * 신청서에 담긴 아이 한 마리.
     *
     * @param photoIds            이 아이의 사진. 1장부터 5장까지
     * @param lowPhotoAcknowledged 1·2장으로 낼 때 보여 준 경고를 확인했는지
     */
    public record Pet(
            @NotBlank @Size(max = 50) String petName,
            @Size(min = 1, max = 5) List<@NotBlank @Size(max = 36) String> photoIds,
            @Size(max = 5) List<@NotBlank @Size(max = 36) String> publicPhotoIds,
            boolean keyringAdded,
            boolean lowPhotoAcknowledged
    ) {
    }

    /** 예전 화면이 쓰던 형태. 아이 목록 없이 낱개 항목만 보낸다. */
    public SubmitGoodsSurveyApplicationRequest(
            String goodsType,
            String customGoods,
            String petName,
            String guardianName,
            String phone,
            String deliveryMethod,
            String postalCode,
            String address,
            String addressDetail,
            List<String> photoIds,
            List<String> publicPhotoIds,
            String conversionEventId,
            JsonNode tracking,
            boolean keyringAdded,
            boolean privacyAgreed,
            boolean shippingConfirmed,
            boolean marketingAgreed
    ) {
        this(goodsType, customGoods, petName, guardianName, phone, deliveryMethod,
                postalCode, address, addressDetail, photoIds, publicPhotoIds,
                conversionEventId, tracking, keyringAdded, privacyAgreed,
                shippingConfirmed, marketingAgreed, null);
    }
}
