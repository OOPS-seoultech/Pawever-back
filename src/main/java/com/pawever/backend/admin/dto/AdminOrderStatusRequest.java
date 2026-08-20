package com.pawever.backend.admin.dto;

import com.pawever.backend.goodssurvey.entity.GoodsOrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminOrderStatusRequest(
        @NotNull GoodsOrderStatus status,
        @Size(max = 300) String memo
) {
}
