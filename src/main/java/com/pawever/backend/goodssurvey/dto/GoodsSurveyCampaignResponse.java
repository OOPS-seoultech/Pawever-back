package com.pawever.backend.goodssurvey.dto;

import java.time.Instant;

public record GoodsSurveyCampaignResponse(
        String campaignId,
        int capacity,
        int allocated,
        int remaining,
        Instant startsAt,
        Instant endsAt,
        boolean open
) {
}
