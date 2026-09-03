package com.pawever.backend.goodssurvey.dto;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SubmitGoodsSurveyApplicationRequest(
        @NotBlank @Size(max = 30) String goodsType,
        @Size(max = 300) String customGoods,
        @NotBlank @Size(max = 50) String petName,
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
        // 얼굴·전신·털무늬 세 종이 제작의 최소 구성이다.
        @Size(min = 3, max = 5) List<@NotBlank @Size(max = 36) String> photoIds,
        @Size(max = 5) List<@NotBlank @Size(max = 36) String> publicPhotoIds,
        @NotBlank @Size(max = 80) String conversionEventId,
        @NotNull JsonNode tracking,
        @AssertTrue boolean privacyAgreed,
        @AssertTrue boolean shippingConfirmed,
        /**
         * 광고성 정보 수신 동의.
         *
         * 개인정보 수집·이용 동의와 달리 강제하지 않는다. 굿즈를 사는 데 필요한
         * 동의와 묶으면 사실상 거부할 수 없게 되어 별도 동의가 아니게 된다.
         */
        boolean marketingAgreed
) {
}
