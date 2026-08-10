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
    void allocationCountIgnoresReservationsAndCountsOnlySubmittedApplications() {
        Instant now = Instant.parse("2026-07-24T09:00:00Z");
        GoodsSurveyCampaign campaign = campaignRepository.save(
                GoodsSurveyCampaign.create(
                        "goods-2026-07",
                        100,
                        27,
                        now.minusSeconds(3600),
                        now.plusSeconds(3600),
                        true,
                        true
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

        long activeAllocations = responseRepository.countSubmittedAllocations(
                campaign.getId(),
                GoodsSurveyResponseStatus.SUBMITTED
        );

        // 노션 기준: 설문과 굿즈 제작 정보를 모두 마친 사람만 자리를 차지한다.
        // 유효한 예약이 있어도 제출 전이면 세지 않는다.
        assertThat(activeAllocations).isEqualTo(1);
        assertThat(campaignRepository.findByIdForUpdate(campaign.getId())).isPresent();
    }

    private GoodsSurveyResponse draft(String suffix, String campaignId) {
        return GoodsSurveyResponse.draft(
                UUID.nameUUIDFromBytes(suffix.getBytes(StandardCharsets.UTF_8)).toString(),
                campaignId,
                "2026-07-25-v2",
                "token-hash-" + suffix,
                "acrylic",
                "{}"
        );
    }
}
