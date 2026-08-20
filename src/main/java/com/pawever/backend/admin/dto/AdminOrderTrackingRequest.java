package com.pawever.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 송장을 등록하면 발송 완료로 넘어간다. */
public record AdminOrderTrackingRequest(
        @NotBlank @Size(max = 50) String trackingCompany,
        @NotBlank @Size(max = 50) String trackingNumber
) {
}
