package com.pawever.backend.memorial.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class MemorialUnreadCountResponse {

    private int totalUnreadCount;

    public static MemorialUnreadCountResponse of(long totalUnreadCount) {
        long safeCount = Math.max(0L, totalUnreadCount);
        int normalizedCount = safeCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) safeCount;

        return MemorialUnreadCountResponse.builder()
                .totalUnreadCount(normalizedCount)
                .build();
    }
}
