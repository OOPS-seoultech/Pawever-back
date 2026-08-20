package com.pawever.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 취소 사유는 반드시 남긴다.
 *
 * 고객에게 왜 취소됐는지 알려야 하고, 나중에 같은 사유가 반복되는지 보려면
 * 기록이 있어야 한다.
 */
public record AdminOrderCancelRequest(
        @NotBlank @Size(max = 300) String reason
) {
}
