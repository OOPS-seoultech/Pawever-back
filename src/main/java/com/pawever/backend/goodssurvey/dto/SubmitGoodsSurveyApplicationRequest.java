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
        @NotBlank @Size(max = 10) String postalCode,
        @NotBlank @Size(max = 200) String address,
        @Size(max = 200) String addressDetail,
        @Size(min = 1, max = 5) List<@NotBlank @Size(max = 36) String> photoIds,
        @NotBlank @Size(max = 80) String conversionEventId,
        @NotNull JsonNode tracking,
        @AssertTrue boolean privacyAgreed,
        @AssertTrue boolean shippingConfirmed
) {
}
