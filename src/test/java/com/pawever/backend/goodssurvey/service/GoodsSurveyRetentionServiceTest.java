package com.pawever.backend.goodssurvey.service;

import com.pawever.backend.goodssurvey.config.GoodsSurveyProperties;
import com.pawever.backend.goodssurvey.entity.GoodsOrderPricing;
import com.pawever.backend.goodssurvey.entity.GoodsOrderStatus;
import com.pawever.backend.goodssurvey.entity.GoodsDeliveryMethod;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyNoticeSubscription;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyPhoto;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyResponse;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyStory;
import com.pawever.backend.goodssurvey.repository.GoodsOrderStatusChangeRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyFulfillmentRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyNoticeSubscriptionRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyPhotoRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyResponseRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyStoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 방침에 적어 둔 보유 기간이 실제로 지켜지는지 확인한다.
 *
 * 기간이 지났는데 남아 있으면 고지 위반이고, 아직인데 지우면 이용자의 기록이
 * 사라진다. 둘 다 되돌릴 수 없어서 경계를 테스트로 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class GoodsSurveyRetentionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-19T04:15:00Z");

    @Mock private GoodsSurveyFulfillmentRepository fulfillmentRepository;
    @Mock private GoodsSurveyNoticeSubscriptionRepository noticeSubscriptionRepository;
    @Mock private GoodsSurveyResponseRepository responseRepository;
    @Mock private GoodsSurveyStoryRepository storyRepository;
    @Mock private GoodsSurveyPhotoRepository photoRepository;
    @Mock private GoodsSurveyPhotoStorage photoStorage;
    @Mock private GoodsOrderStatusChangeRepository statusChangeRepository;
    @Mock private GoodsOrderService orderService;

    private GoodsSurveyRetentionService retentionService;

    @BeforeEach
    void setUp() {
        GoodsSurveyProperties properties = new GoodsSurveyProperties();
        retentionService = new GoodsSurveyRetentionService(
                fulfillmentRepository,
                noticeSubscriptionRepository,
                responseRepository,
                storyRepository,
                photoRepository,
                photoStorage,
                statusChangeRepository,
                properties,
                orderService
        );
    }

    @Test
    void 배송이_끝나면_사진과_상세주소를_지우고_계약_기록은_남긴다() {
        // 유료 판매는 주문·결제·공급 기록을 5년 보존해야 한다. 90일에 행을
        // 통째로 지우면 법정 보존 기록까지 사라진다.
        GoodsSurveyFulfillment fulfillment = fulfillment("resp-1");
        GoodsSurveyPhoto photo = photo("photo-1", "resp-1", "goods/resp-1/1.jpg");

        when(fulfillmentRepository.findByDeleteAfterNotNullAndDeleteAfterLessThanEqual(eq(NOW), any(Limit.class)))
                .thenReturn(List.of(fulfillment));
        when(photoRepository.findByResponseIdAndPublicationAgreedFalse("resp-1"))
                .thenReturn(List.of(photo));

        int purged = retentionService.purgeDeliveredFulfillments(NOW);

        assertThat(purged).isEqualTo(1);
        verify(photoStorage).delete("goods/resp-1/1.jpg");
        verify(photoRepository).delete(photo);
        verify(fulfillmentRepository, never()).delete(any());
        assertThat(fulfillment.getAddressDetail()).isNull();
        // 어디로 보냈는지는 공급 기록이라 남긴다.
        assertThat(fulfillment.getAddress()).isNotNull();
    }

    @Test
    void 공개에_동의한_사진은_배송_기간이_지나도_남긴다() {
        GoodsSurveyFulfillment fulfillment = fulfillment("resp-1");

        when(fulfillmentRepository.findByDeleteAfterNotNullAndDeleteAfterLessThanEqual(eq(NOW), any(Limit.class)))
                .thenReturn(List.of(fulfillment));
        // 공개 동의 사진은 조회 자체에서 빠진다. 제작이 아니라 공개가 목적이라
        // 설문과 같은 2년을 따르고, 그때 함께 지워진다.
        when(photoRepository.findByResponseIdAndPublicationAgreedFalse("resp-1"))
                .thenReturn(List.of());

        retentionService.purgeDeliveredFulfillments(NOW);

        verify(photoStorage, never()).delete(any());
    }

    @Test
    void 법정_보존_기간이_지나면_계약_기록과_응답을_함께_지운다() {
        GoodsSurveyFulfillment fulfillment = fulfillment("resp-1");
        GoodsSurveyResponse response = response("resp-1");

        when(fulfillmentRepository
                .findByContractDeleteAfterNotNullAndContractDeleteAfterLessThanEqual(eq(NOW), any(Limit.class)))
                .thenReturn(List.of(fulfillment));
        when(photoRepository.findByResponseId("resp-1")).thenReturn(List.of());
        when(storyRepository.findByResponseId("resp-1")).thenReturn(Optional.empty());
        when(responseRepository.findById("resp-1")).thenReturn(Optional.of(response));

        int purged = retentionService.purgeExpiredContracts(NOW);

        assertThat(purged).isEqualTo(1);
        verify(fulfillmentRepository).delete(fulfillment);
        verify(responseRepository).delete(response);
    }

    @Test
    void 파일을_먼저_지우고_행을_지운다() {
        GoodsSurveyFulfillment fulfillment = fulfillment("resp-1");
        GoodsSurveyPhoto photo = photo("photo-1", "resp-1", "goods/resp-1/1.jpg");

        when(fulfillmentRepository.findByDeleteAfterNotNullAndDeleteAfterLessThanEqual(eq(NOW), any(Limit.class)))
                .thenReturn(List.of(fulfillment));
        when(photoRepository.findByResponseIdAndPublicationAgreedFalse("resp-1"))
                .thenReturn(List.of(photo));
        // 저장소가 실패하면 행이 남아야 다음 회차에 다시 잡힌다.
        // 순서가 뒤집히면 어떤 파일을 지워야 하는지 알 방법이 사라진다.
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(photoStorage, photoRepository);

        retentionService.purgeDeliveredFulfillments(NOW);

        order.verify(photoStorage).delete("goods/resp-1/1.jpg");
        order.verify(photoRepository).delete(photo);
    }

    @Test
    void 기간이_지났거나_수신을_거부한_안내_이메일을_지운다() {
        GoodsSurveyNoticeSubscription subscription = GoodsSurveyNoticeSubscription.create(
                "goods-2026-07",
                "a@example.com",
                "hash",
                "2026-07-23",
                NOW.minusSeconds(400 * 86_400L),
                365
        );

        when(noticeSubscriptionRepository.findPurgeTargets(eq(NOW), any(Limit.class)))
                .thenReturn(List.of(subscription));

        int purged = retentionService.purgeNoticeSubscriptions(NOW);

        assertThat(purged).isEqualTo(1);
        verify(noticeSubscriptionRepository).deleteAll(List.of(subscription));
    }

    @Test
    void 설문은_수집일로부터_2년이_지나면_사연과_함께_지운다() {
        GoodsSurveyResponse response = response("resp-1");
        GoodsSurveyStory story = GoodsSurveyStory.create("resp-1", "{}", true, true, "2026-07-23", NOW);
        GoodsSurveyPhoto photo = photo("photo-1", "resp-1", "goods/resp-1/1.jpg");

        when(responseRepository.findByCreatedAtLessThanEqual(any(LocalDateTime.class), any(Limit.class)))
                .thenReturn(List.of(response));
        when(photoRepository.findByResponseId("resp-1")).thenReturn(List.of(photo));
        when(storyRepository.findByResponseId("resp-1")).thenReturn(Optional.of(story));
        when(fulfillmentRepository.findByResponseId("resp-1")).thenReturn(Optional.empty());

        int purged = retentionService.purgeExpiredSurveys(NOW);

        assertThat(purged).isEqualTo(1);
        verify(photoStorage).delete("goods/resp-1/1.jpg");
        verify(storyRepository).delete(story);
        verify(responseRepository).delete(response);
    }

    @Test
    void 설문을_지울_때_기준일은_수집일로부터_설정한_보유기간_전이다() {
        when(responseRepository.findByCreatedAtLessThanEqual(any(LocalDateTime.class), any(Limit.class)))
                .thenReturn(List.of());

        retentionService.purgeExpiredSurveys(NOW);

        ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(responseRepository).findByCreatedAtLessThanEqual(threshold.capture(), any(Limit.class));
        // 2026-08-19 에서 730일 전. 2024 는 윤년이지만 2024-08-19 이후라 셈에 영향이 없다.
        assertThat(threshold.getValue()).isEqualTo(LocalDateTime.parse("2024-08-19T04:15:00"));
    }

    @Test
    void 설문_2년이_지나도_법정_보존_기간이_남은_계약_기록은_건드리지_않는다() {
        // 설문은 2년, 계약 기록은 5년이다. 짧은 쪽 기준으로 지우면 법정
        // 보존 기록이 3년 일찍 사라진다. 응답도 함께 남겨야 그 기록이 어느
        // 주문의 것인지 알 수 있다.
        GoodsSurveyResponse response = response("resp-1");
        GoodsSurveyFulfillment fulfillment = fulfillment("resp-1");

        when(responseRepository.findByCreatedAtLessThanEqual(any(LocalDateTime.class), any(Limit.class)))
                .thenReturn(List.of(response));
        when(fulfillmentRepository.findByResponseId("resp-1")).thenReturn(Optional.of(fulfillment));

        int purged = retentionService.purgeExpiredSurveys(NOW);

        assertThat(purged).isZero();
        verify(fulfillmentRepository, never()).delete(any());
        verify(responseRepository, never()).delete(any());
    }

    @Test
    void 신청_기록이_없는_설문은_2년이_지나면_그대로_지운다() {
        GoodsSurveyResponse response = response("resp-1");

        when(responseRepository.findByCreatedAtLessThanEqual(any(LocalDateTime.class), any(Limit.class)))
                .thenReturn(List.of(response));
        when(fulfillmentRepository.findByResponseId("resp-1")).thenReturn(Optional.empty());
        when(photoRepository.findByResponseId("resp-1")).thenReturn(List.of());
        when(storyRepository.findByResponseId("resp-1")).thenReturn(Optional.empty());

        int purged = retentionService.purgeExpiredSurveys(NOW);

        assertThat(purged).isEqualTo(1);
        verify(responseRepository).delete(response);
    }

    @Test
    void 결제하지_않은_주문은_30분이_지나면_사진까지_지운다() {
        // 계약이 성립하지 않았으니 반려견 사진을 들고 있을 근거가 없다.
        // 공개 동의 여부와 무관하게 전부 지운다 — 공개 동의는 굿즈를 만드는
        // 것을 전제로 받은 것이고, 만들지 않기로 된 주문이다.
        GoodsSurveyFulfillment fulfillment = fulfillment("resp-1");
        GoodsSurveyPhoto photo = photo("photo-1", "resp-1", "goods/resp-1/1.jpg");

        when(fulfillmentRepository.findByStatusAndPaymentExpiresAtLessThanEqual(
                eq(GoodsOrderStatus.PAYMENT_PENDING), eq(NOW), any(Limit.class)))
                .thenReturn(List.of(fulfillment));
        when(photoRepository.findByResponseId("resp-1")).thenReturn(List.of(photo));

        int expired = retentionService.expireUnpaidOrders(NOW);

        assertThat(expired).isEqualTo(1);
        verify(photoStorage).delete("goods/resp-1/1.jpg");
        assertThat(fulfillment.getStatus()).isEqualTo(GoodsOrderStatus.PAYMENT_EXPIRED);
        assertThat(fulfillment.getAddressDetail()).isNull();
        // 행은 남긴다. 무엇이 왜 만료됐는지 관리자가 볼 수 있어야 한다.
        verify(fulfillmentRepository, never()).delete(any());
        verify(orderService).recordSystemChange(
                eq("resp-1"),
                eq(GoodsOrderStatus.PAYMENT_PENDING),
                eq(GoodsOrderStatus.PAYMENT_EXPIRED),
                any()
        );
    }

    private GoodsSurveyFulfillment fulfillment(String responseId) {
        return GoodsSurveyFulfillment.create(
                responseId,
                "idem-" + responseId,
                "conv-" + responseId,
                "{}",
                "keyring",
                null,
                "콩이",
                "보호자",
                "01012345678",
                "phone-hash-" + responseId,
                GoodsDeliveryMethod.SHIPPING,
                "01234",
                "서울시 어딘가",
                "101호",
                "2026-07-23",
                NOW.minusSeconds(200 * 86_400L),
                true,
                "PE-2026-000001",
                GoodsOrderPricing.discounted(29_900, 5_000, "설문 참여 할인", 3_000),
                false,
                "marketing-v1",
                30,
                1825
        );
    }

    private GoodsSurveyResponse response(String id) {
        return GoodsSurveyResponse.draft(id, "goods-2026-07", "2026-07-25-v2", "token-hash", "keyring", "{}");
    }

    private GoodsSurveyPhoto photo(String id, String responseId, String objectKey) {
        return GoodsSurveyPhoto.pending(id, responseId, "client-" + id, objectKey, "image/jpeg", 1024L, NOW);
    }
}
