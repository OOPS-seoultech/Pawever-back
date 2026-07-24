package com.pawever.backend.goodssurvey.repository;

import com.pawever.backend.goodssurvey.entity.GoodsSurveyCampaign;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyResponse;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyResponseStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class GoodsSurveyRepositoryTest {

    @Autowired private GoodsSurveyCampaignRepository campaignRepository;
    @Autowired private GoodsSurveyResponseRepository responseRepository;

    @Test
    void allocationCountIncludesSubmittedAndOnlyUnexpiredReservations() {
        Instant now = Instant.parse("2026-07-24T09:00:00Z");
        GoodsSurveyCampaign campaign = campaignRepository.save(
                GoodsSurveyCampaign.create(
                        "goods-2026-07",
                        100,
                        27,
                        now.minusSeconds(3600),
                        now.plusSeconds(3600)
                )
        );

        GoodsSurveyResponse active = draft("active", campaign.getId());
        active.reserve(now, now.plusSeconds(600));
        responseRepository.save(active);

        GoodsSurveyResponse expired = draft("expired", campaign.getId());
        expired.reserve(now.minusSeconds(1200), now.minusSeconds(600));
        responseRepository.save(expired);

        GoodsSurveyResponse submitted = draft("submitted", campaign.getId());
        submitted.reserve(now.minusSeconds(600), now.plusSeconds(600));
        submitted.submit();
        responseRepository.save(submitted);

        long activeAllocations = responseRepository.countActiveAllocations(
                campaign.getId(),
                now,
                GoodsSurveyResponseStatus.RESERVED,
                GoodsSurveyResponseStatus.SUBMITTED
        );

        assertThat(activeAllocations).isEqualTo(2);
        assertThat(campaignRepository.findByIdForUpdate(campaign.getId())).isPresent();
    }

    private GoodsSurveyResponse draft(String suffix, String campaignId) {
        return GoodsSurveyResponse.draft(
                UUID.nameUUIDFromBytes(suffix.getBytes(StandardCharsets.UTF_8)).toString(),
                campaignId,
                "2026-07-23-v1",
                "token-hash-" + suffix,
                "acrylic",
                "{}"
        );
    }
}
