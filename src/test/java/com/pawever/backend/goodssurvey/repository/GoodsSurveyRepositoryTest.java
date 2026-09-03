package com.pawever.backend.goodssurvey.repository;

import com.pawever.backend.goodssurvey.entity.GoodsOrderPricing;
import com.pawever.backend.goodssurvey.entity.GoodsOrderStatus;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyCampaign;
import com.pawever.backend.goodssurvey.entity.GoodsDeliveryMethod;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyResponse;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyResponseStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.pawever.backend.global.common.EncryptedStringConverter;
import com.pawever.backend.global.security.AesEncryptor;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
/*
 * 주문에는 연락처·주소처럼 암호화해 담는 칸이 있다. @DataJpaTest 는 JPA 만
 * 올리므로 변환기가 쓰는 암호기가 빈으로 없고, 저장하는 순간 널로 터진다.
 * 정원을 세려면 주문을 실제로 저장해 봐야 해서 둘을 같이 올린다.
 */
@Import({AesEncryptor.class, EncryptedStringConverter.class})
class GoodsSurveyRepositoryTest {

    @Autowired private GoodsSurveyCampaignRepository campaignRepository;
    @Autowired private GoodsSurveyResponseRepository responseRepository;
    @Autowired private GoodsSurveyFulfillmentRepository fulfillmentRepository;

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
                GoodsSurveyResponseStatus.SUBMITTED,
                GoodsOrderStatus.releasesSlot()
        );

        // 노션 기준: 설문과 굿즈 제작 정보를 모두 마친 사람만 자리를 차지한다.
        // 유효한 예약이 있어도 제출 전이면 세지 않는다.
        assertThat(activeAllocations).isEqualTo(1);
        assertThat(campaignRepository.findByIdForUpdate(campaign.getId())).isPresent();
    }

    /**
     * 죽은 주문은 자리를 놓아야 한다.
     *
     * 자리를 세는 기준은 응답의 SUBMITTED 인데, 만료·취소 처리는 주문 쪽
     * 상태만 바꾸고 응답은 SUBMITTED 로 둔다. 그래서 결제되지 않아 만료된
     * 주문이 정원 한 칸을 영원히 잡고 있었다.
     *
     * 2026-08-30 접수된 테스트 주문 PE-2026-000101 이 만료된 뒤에도
     * allocated 가 1 로 남아 있었다. 100명 모집이면 실제로 팔 수 있는 것이
     * 99개가 된다. 만료가 쌓이면 아무도 신청하지 못하는 상태로 닫힌다.
     */
    @Test
    void 만료_실패_취소된_주문은_자리를_놓는다() {
        Instant now = Instant.parse("2026-08-30T09:00:00Z");
        GoodsSurveyCampaign campaign = campaignRepository.save(
                GoodsSurveyCampaign.create(
                        "goods-2026-09", 100, 0,
                        now.minusSeconds(3600), now.plusSeconds(3600), true, true
                )
        );

        submittedWithOrder("live", campaign.getId(), now, GoodsOrderStatus.PAYMENT_PENDING);
        submittedWithOrder("paid", campaign.getId(), now, GoodsOrderStatus.PAYMENT_COMPLETED);
        submittedWithOrder("expired", campaign.getId(), now, GoodsOrderStatus.PAYMENT_EXPIRED);
        submittedWithOrder("failed", campaign.getId(), now, GoodsOrderStatus.PAYMENT_FAILED);
        submittedWithOrder("canceled", campaign.getId(), now, GoodsOrderStatus.CANCELED);

        long active = responseRepository.countSubmittedAllocations(
                campaign.getId(), GoodsSurveyResponseStatus.SUBMITTED,
                GoodsOrderStatus.releasesSlot()
        );

        // 살아 있는 둘만 남는다. 결제를 기다리는 건은 아직 자리를 잡고 있다 —
        // 기다리는 동안 다른 사람이 그 자리를 가져가면 낼 돈이 사라진다.
        assertThat(active).isEqualTo(2);
    }

    @Test
    void 취소에_실패한_주문은_자리를_계속_잡는다() {
        // 돈은 받아 두고 환불에 실패한 상태다. 사람이 정리하기 전까지 그 물건은
        // 이 사람 몫이다. 여기서 자리를 놓으면 환불도 못 한 채로 같은 자리를
        // 다른 사람에게 판다.
        Instant now = Instant.parse("2026-08-30T09:00:00Z");
        GoodsSurveyCampaign campaign = campaignRepository.save(
                GoodsSurveyCampaign.create(
                        "goods-2026-10", 100, 0,
                        now.minusSeconds(3600), now.plusSeconds(3600), true, true
                )
        );

        submittedWithOrder("cancelfail", campaign.getId(), now, GoodsOrderStatus.CANCEL_FAILED);

        assertThat(responseRepository.countSubmittedAllocations(
                campaign.getId(), GoodsSurveyResponseStatus.SUBMITTED,
                GoodsOrderStatus.releasesSlot()
        )).isEqualTo(1);
    }

    @Test
    void 주문_기록이_없는_제출은_자리를_잡은_것으로_센다() {
        // 제출하면 주문이 함께 생기므로 정상 흐름에서는 나오지 않는다. 그래도
        // 없을 때 자리를 놓아 버리면 정원을 넘겨 파는 쪽으로 넘어진다.
        Instant now = Instant.parse("2026-08-30T09:00:00Z");
        GoodsSurveyCampaign campaign = campaignRepository.save(
                GoodsSurveyCampaign.create(
                        "goods-2026-11", 100, 0,
                        now.minusSeconds(3600), now.plusSeconds(3600), true, true
                )
        );

        GoodsSurveyResponse orphan = draft("orphan", campaign.getId());
        orphan.startDirectPurchase(now);
        orphan.submit();
        responseRepository.save(orphan);

        assertThat(responseRepository.countSubmittedAllocations(
                campaign.getId(), GoodsSurveyResponseStatus.SUBMITTED,
                GoodsOrderStatus.releasesSlot()
        )).isEqualTo(1);
    }

    private void submittedWithOrder(
            String suffix, String campaignId, Instant now, GoodsOrderStatus status
    ) {
        GoodsSurveyResponse response = draft(suffix, campaignId);
        response.startDirectPurchase(now);
        response.submit();
        responseRepository.save(response);

        GoodsSurveyFulfillment fulfillment = GoodsSurveyFulfillment.create(
                response.getId(), "idem-" + suffix, "conv-" + suffix, "{}",
                "figure", null, "보리", "황성욱", "010-1234-5678", "hash-" + suffix,
                GoodsDeliveryMethod.SHIPPING,
                "01811", "서울 노원구", "101호", "v1", now, false,
                // 주문번호 칸은 20자이고 값은 유일해야 한다. 접미사 길이가
                // 제각각이라 뒤를 0으로 채워 길이를 고정한다.
                String.format("PE-%-13s", suffix).replace(' ', '0'),
                new GoodsOrderPricing(29900, 0, null, 3000, 32900),
                false, null, 30, 1825
        );
        fulfillment.changeStatus(status);
        fulfillmentRepository.save(fulfillment);
    }

    private GoodsSurveyResponse draft(String suffix, String campaignId) {
        return GoodsSurveyResponse.draft(
                UUID.nameUUIDFromBytes(suffix.getBytes(StandardCharsets.UTF_8)).toString(),
                campaignId,
                "2026-07-25-v2",
                "token-hash-" + suffix,
                "figure",
                "{}"
        );
    }
}
