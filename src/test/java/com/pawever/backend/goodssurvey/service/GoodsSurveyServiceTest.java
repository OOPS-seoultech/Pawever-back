package com.pawever.backend.goodssurvey.service;

import com.pawever.backend.global.security.HmacHasher;
import com.pawever.backend.goodssurvey.config.GoodsSurveyProperties;
import com.pawever.backend.goodssurvey.dto.CreateGoodsSurveyPhotoUploadRequest;
import com.pawever.backend.goodssurvey.dto.CreateGoodsSurveyRequest;
import com.pawever.backend.goodssurvey.dto.GoodsSurveyCompletionResponse;
import com.pawever.backend.goodssurvey.dto.GoodsSurveyDraftResponse;
import com.pawever.backend.goodssurvey.dto.SaveGoodsSurveyDraftRequest;
import com.pawever.backend.goodssurvey.dto.SaveGoodsSurveyStoryRequest;
import com.pawever.backend.goodssurvey.dto.SubmitGoodsSurveyApplicationRequest;
import com.pawever.backend.goodssurvey.dto.SubscribeGoodsSurveyNoticeRequest;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyCampaign;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyNoticeSubscription;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyPhoto;
import com.pawever.backend.goodssurvey.entity.GoodsOrderStatus;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyResponse;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyResponseStatus;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyStory;
import com.pawever.backend.goodssurvey.event.GoodsOrderSubmittedEvent;
import com.pawever.backend.goodssurvey.entity.GoodsOrderSequence;
import com.pawever.backend.goodssurvey.repository.GoodsOrderSequenceRepository;
import com.pawever.backend.goodssurvey.repository.GoodsOrderStatusChangeRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyCampaignRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyFulfillmentRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyNoticeSubscriptionRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyPhotoRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyResponseRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyStoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import com.pawever.backend.goodssurvey.entity.GoodsDeliveryMethod;
import com.pawever.backend.goodssurvey.entity.GoodsSalesChannel;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoodsSurveyServiceTest {

    private GoodsSurveyProperties properties;

    private static final Instant NOW = Instant.parse("2026-07-24T09:00:00Z");

    @Mock private GoodsSurveyCampaignRepository campaignRepository;
    @Mock private GoodsSurveyResponseRepository responseRepository;
    @Mock private GoodsSurveyStoryRepository storyRepository;
    @Mock private GoodsSurveyFulfillmentRepository fulfillmentRepository;
    @Mock private GoodsSurveyPhotoRepository photoRepository;
    @Mock private GoodsSurveyNoticeSubscriptionRepository noticeSubscriptionRepository;
    @Mock private GoodsSurveyPhotoStorage photoStorage;
    @Mock private GoodsOrderSequenceRepository sequenceRepository;
    @Mock private GoodsOrderStatusChangeRepository statusChangeRepository;

    private GoodsSurveyService service;
    private GoodsSurveyCampaign campaign;
    // 예약 만료 같은 시간 의존 동작을 재현하려면 시계를 움직일 수 있어야 한다.
    private Instant currentTime = NOW;
    private final AtomicReference<GoodsSurveyResponse> storedResponse = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        properties = new GoodsSurveyProperties();
        properties.setCampaignId("goods-2026-07");
        properties.setReservationMinutes(15);

        useCampaign(true, true);
        // 설문이 닫힌 경우처럼 응답을 만들기도 전에 끝나는 테스트가 있어서
        // 응답 저장·조회 스텁은 쓰이지 않을 수 있다.
        lenient().when(responseRepository.save(any(GoodsSurveyResponse.class)))
                .thenAnswer(invocation -> {
                    GoodsSurveyResponse response = invocation.getArgument(0);
                    storedResponse.set(response);
                    return response;
                });
        lenient().when(responseRepository.findById(any()))
                .thenAnswer(invocation -> Optional.ofNullable(storedResponse.get()));
        lenient().when(responseRepository.countSubmittedAllocations(any(), any(), any()))
                .thenReturn(0L);

        Clock testClock = new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return currentTime;
            }
        };

        // 채번기는 한 덩어리를 돌려준다. 번호가 실제로 하나씩 올라가야
        // 같은 테스트에서 두 번 신청했을 때 겹치지 않는 것을 볼 수 있다.
        GoodsOrderSequence sequence = GoodsOrderSequence.startOf(2026);
        lenient().when(sequenceRepository.findByYearForUpdate(anyInt()))
                .thenReturn(Optional.of(sequence));

        GoodsOrderService orderService = new GoodsOrderService(
                sequenceRepository,
                statusChangeRepository,
                properties,
                testClock
        );

        publishedEvents = new ArrayList<>();
        service = new GoodsSurveyService(
                campaignRepository,
                responseRepository,
                storyRepository,
                fulfillmentRepository,
                photoRepository,
                noticeSubscriptionRepository,
                photoStorage,
                new GoodsSurveyAnswerValidator(new ObjectMapper()),
                new ObjectMapper(),
                new HmacHasher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="),
                properties,
                orderService,
                testClock,
                publishedEvents::add
        );
    }

    /**
     * 접수가 무엇을 알렸는지 붙들어 둔다.
     *
     * 실제로는 커밋된 뒤에 다른 스레드가 받아서 텔레그램으로 보낸다. 여기서
     * 보는 것은 거기까지 가는 값이 맞느냐다.
     */
    private List<Object> publishedEvents;

    /**
     * 설문·굿즈 스위치를 바꿔 끼운다.
     *
     * 서비스가 호출할 때마다 캠페인을 새로 읽으므로, 테스트 중간에 갈아 끼우면
     * 양식을 작성하는 사이에 굿즈가 닫히는 상황도 그대로 재현된다.
     */
    private void useCampaign(boolean surveyOpen, boolean goodsOpen) {
        campaign = GoodsSurveyCampaign.create(
                "goods-2026-07",
                100,
                27,
                Instant.parse("2026-07-23T00:00:00Z"),
                Instant.parse("2026-08-05T14:59:59Z"),
                surveyOpen,
                goodsOpen
        );
        lenient().when(campaignRepository.findById("goods-2026-07"))
                .thenReturn(Optional.of(campaign));
        lenient().when(campaignRepository.findByIdForUpdate("goods-2026-07"))
                .thenReturn(Optional.of(campaign));
    }

    /**
     * 플리마켓 모집을 끼운다.
     *
     * 설문 스위치는 닫아 둔다. QR 을 찍고 바로 주문하는 자리라 설문을 거치지
     * 않고, 문은 굿즈 스위치가 지킨다.
     */
    private void useFleaCampaign(boolean goodsOpen) {
        GoodsSurveyCampaign flea = GoodsSurveyCampaign.create(
                "goods-2026-09-flea",
                GoodsSalesChannel.FLEA,
                70,
                0,
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-12-31T14:59:59Z"),
                false,
                goodsOpen
        );
        lenient().when(campaignRepository.findById("goods-2026-09-flea"))
                .thenReturn(Optional.of(flea));
        lenient().when(campaignRepository.findByIdForUpdate("goods-2026-09-flea"))
                .thenReturn(Optional.of(flea));
    }

    @Test
    void 플리마켓으로_들어오면_현장가로_접수된다() {
        // 근거: [피그마 0uW99BqaTJKUVlowzQswli / 8-2 Rending Page]
        //       5472:1478 "서울과학기술대학교 플리마켓 전용가",
        //       5472:1480 "11,900원", 5498:2395 "60.2% 할인",
        //       5472:1773 "배송비 3,000원 별도"
        //
        // 29,900 - 18,000 = 11,900 이고 18,000 / 29,900 = 60.2% 다. 디자인이
        // 기존 정가를 기준으로 잡아 둔 값이라 할인 하나로 들어간다.
        properties.setFleaCampaignId("goods-2026-09-flea");
        useFleaCampaign(true);
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tracking = objectMapper.createObjectNode().put("visitId", "visit-flea");
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "figure", tracking, "flea")
        );
        service.startDirectPurchase(draft.responseId(), draft.editToken());
        when(photoRepository.findAllByIdInAndResponseIdAndStatus(any(), any(), any()))
                .thenReturn(confirmedPhotos("photo-flea", draft.responseId()));

        ArgumentCaptor<GoodsSurveyFulfillment> saved =
                ArgumentCaptor.forClass(GoodsSurveyFulfillment.class);
        service.submitApplication(
                draft.responseId(),
                draft.editToken(),
                "idempotency-flea",
                directApplication(tracking, "conversion-flea", "photo-flea")
        );

        verify(fulfillmentRepository).save(saved.capture());
        assertThat(saved.getValue().getDiscountAmountKrw()).isEqualTo(18_000);
        assertThat(saved.getValue().getPromotionName()).isEqualTo("과기대 플리마켓 할인");
        // 제작비 11,900 + 배송비 3,000
        assertThat(saved.getValue().getPaymentAmountKrw()).isEqualTo(14_900);
    }

    @Test
    void 현장_수령이면_배송비도_주소도_없다() {
        // 근거: [피그마 0uW99BqaTJKUVlowzQswli / 8-2 Rending Page]
        //       5472:1482 "방문수령 외 택배 시 배송비 3,000원 별도"
        //       5472:1755 "선착순 70명 예약하고, 과기대에서 수령하기"
        //
        // 부치지 않으니 배송비가 없고, 받는 사람이 그 자리에 오니 주소도 없다.
        // 적어 보냈더라도 남기지 않는다 — 쓰지 않을 주소를 보관하면 지킬 것만
        // 늘어난다.
        properties.setFleaCampaignId("goods-2026-09-flea");
        useFleaCampaign(true);
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tracking = objectMapper.createObjectNode().put("visitId", "visit-pickup");
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "figure", tracking, "flea")
        );
        service.startDirectPurchase(draft.responseId(), draft.editToken());
        when(photoRepository.findAllByIdInAndResponseIdAndStatus(any(), any(), any()))
                .thenReturn(confirmedPhotos("photo-pickup", draft.responseId()));

        ArgumentCaptor<GoodsSurveyFulfillment> saved =
                ArgumentCaptor.forClass(GoodsSurveyFulfillment.class);
        service.submitApplication(
                draft.responseId(),
                draft.editToken(),
                "idempotency-pickup",
                new SubmitGoodsSurveyApplicationRequest(
                        "figure",
                        "",
                        "몽이",
                        "보호자",
                        "01012345678",
                        "pickup",
                        "01234",
                        "서울시 노원구",
                        "101동 202호",
                        photoIds("photo-pickup"),
                        List.of(),
                        "conversion-pickup",
                        tracking,
                        true,
                        true,
                        false
                )
        );

        verify(fulfillmentRepository).save(saved.capture());
        assertThat(saved.getValue().getDeliveryMethod())
                .isEqualTo(GoodsDeliveryMethod.PICKUP);
        assertThat(saved.getValue().getShippingFeeKrw()).isZero();
        assertThat(saved.getValue().getPaymentAmountKrw()).isEqualTo(11_900);
        assertThat(saved.getValue().getPostalCode()).isNull();
        assertThat(saved.getValue().getAddress()).isNull();
        assertThat(saved.getValue().getAddressDetail()).isNull();
    }

    @Test
    void 현장_수령은_플리마켓이_아니면_고를_수_없다() {
        // 상시 온라인 판매에는 건네줄 자리가 없다. 여기서 열어 두면 부칠 곳
        // 없는 주문이 들어오고, 행사가 끝난 뒤에도 그대로 남는다.
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tracking = objectMapper.createObjectNode().put("visitId", "visit-pickup-online");
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "figure", tracking, null)
        );
        service.startDirectPurchase(draft.responseId(), draft.editToken());
        lenient().when(photoRepository.findAllByIdInAndResponseIdAndStatus(any(), any(), any()))
                .thenReturn(confirmedPhotos("photo-pickup-online", draft.responseId()));

        assertThatThrownBy(() -> service.submitApplication(
                draft.responseId(),
                draft.editToken(),
                "idempotency-pickup-online",
                new SubmitGoodsSurveyApplicationRequest(
                        "figure",
                        "",
                        "몽이",
                        "보호자",
                        "01012345678",
                        "pickup",
                        "01234",
                        "서울시 노원구",
                        "",
                        photoIds("photo-pickup-online"),
                        List.of(),
                        "conversion-pickup-online",
                        tracking,
                        true,
                        true,
                        false
                )
        )).hasMessageContaining("현장 수령을 고를 수 없는");
    }

    @Test
    void 부쳐야_하는데_주소가_없으면_접수되지_않는다() {
        // 주소를 선택으로 바꾼 것은 현장 수령 때문이다. 부치는 주문까지 주소
        // 없이 통과하면 만들어 놓고 보낼 곳이 없다.
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tracking = objectMapper.createObjectNode().put("visitId", "visit-no-address");
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "figure", tracking, null)
        );
        service.startDirectPurchase(draft.responseId(), draft.editToken());
        lenient().when(photoRepository.findAllByIdInAndResponseIdAndStatus(any(), any(), any()))
                .thenReturn(confirmedPhotos("photo-no-address", draft.responseId()));

        assertThatThrownBy(() -> service.submitApplication(
                draft.responseId(),
                draft.editToken(),
                "idempotency-no-address",
                new SubmitGoodsSurveyApplicationRequest(
                        "figure",
                        "",
                        "몽이",
                        "보호자",
                        "01012345678",
                        null,
                        "",
                        "",
                        "",
                        photoIds("photo-no-address"),
                        List.of(),
                        "conversion-no-address",
                        tracking,
                        true,
                        true,
                        false
                )
        )).hasMessageContaining("입력");
    }

    @Test
    void 플리마켓_모집을_정하지_않으면_그_경로는_열리지_않는다() {
        // 행사가 끝나면 설정을 비워 닫는다. 배포 없이 환경변수만 비우면 되고,
        // 그러면 새 주문이 들어올 길 자체가 사라진다.
        properties.setFleaCampaignId("");
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tracking = objectMapper.createObjectNode().put("visitId", "visit-flea-off");

        assertThatThrownBy(() -> service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "figure", tracking, "flea")
        )).hasMessageContaining("열려 있지 않은 판매 경로");
    }

    @Test
    void 설정이_가리키는_모집이_다른_경로면_받지_않는다() {
        // 설정과 모집이 어긋난 채로 통과시키면 플리마켓으로 들어온 사람에게
        // 온라인 값 29,900 이 매겨진다. 값을 틀리게 매기느니 열지 않는다.
        properties.setFleaCampaignId("goods-2026-07");
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tracking = objectMapper.createObjectNode().put("visitId", "visit-flea-mixed");

        assertThatThrownBy(() -> service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "figure", tracking, "flea")
        )).hasMessageContaining("열려 있지 않은 판매 경로");
    }

    @Test
    void surveyKeepsAcceptingAnswersAfterTheGoodsCampaignIsClosed() {
        // 1차 무료 제작이 끝난 상태. 정원이 남아 있어도 굿즈는 열리지 않아야 하고,
        // 설문은 그와 무관하게 계속 받아야 한다.
        useCampaign(true, false);
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tracking = objectMapper.createObjectNode().put("visitId", "visit-goods-closed");

        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "figure", tracking, null)
        );

        GoodsSurveyCompletionResponse result = service.completeSurvey(
                draft.responseId(),
                draft.editToken(),
                new SaveGoodsSurveyDraftRequest(
                        reservableAnswers(),
                        "q33",
                        30_000L,
                        Map.of(),
                        tracking
                )
        );

        assertThat(result.status()).isEqualTo("COMPLETED_NO_SLOT");
        assertThat(storedResponse.get().getStatus())
                .isEqualTo(GoodsSurveyResponseStatus.COMPLETED_NO_SLOT);
        assertThat(storedResponse.get().getAnswersJson()).contains("\"q1\":\"current_only\"");
        // 자리는 73이 남아 있다. 그런데도 굿즈가 닫힌 이유는 스위치이지 정원이 아니다.
        assertThat(result.remaining()).isEqualTo(73);
    }

    @Test
    void storyIsAcceptedFromSomeoneWhoFinishedTheSurveyWithoutAGoodsSlot() {
        // 굿즈가 닫혀도 설문을 계속 받는 이유가 사연이다. 자리를 못 받았다고
        // 사연까지 막으면 설문을 열어 둔 의미가 없다.
        useCampaign(true, false);
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tracking = objectMapper.createObjectNode().put("visitId", "visit-story-no-slot");
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "figure", tracking, null)
        );
        service.completeSurvey(
                draft.responseId(),
                draft.editToken(),
                new SaveGoodsSurveyDraftRequest(
                        reservableAnswers(),
                        "q33",
                        30_000L,
                        Map.of(),
                        tracking
                )
        );
        assertThat(storedResponse.get().getStatus())
                .isEqualTo(GoodsSurveyResponseStatus.COMPLETED_NO_SLOT);
        when(storyRepository.findByResponseId(draft.responseId()))
                .thenReturn(Optional.empty());
        AtomicReference<GoodsSurveyStory> savedStory = new AtomicReference<>();
        when(storyRepository.save(any(GoodsSurveyStory.class)))
                .thenAnswer(invocation -> {
                    savedStory.set(invocation.getArgument(0));
                    return invocation.getArgument(0);
                });

        service.saveStory(
                draft.responseId(),
                draft.editToken(),
                new SaveGoodsSurveyStoryRequest(
                        "함께 살고 있다",
                        "9~11세",
                        "작은 노화나 이상 변화",
                        "산책길에서 문득 걸음이 느려진 걸 알았다.",
                        "", "", "", "", "", "", "", "",
                        true,
                        false
                )
        );

        assertThat(savedStory.get()).isNotNull();
        assertThat(savedStory.get().getStoryJson()).contains("걸음이 느려진");
    }

    @Test
    void photoUploadIsRefusedWithoutAGoodsSlot() {
        // 사진은 굿즈 제작에만 쓴다고 고지하고 받는다. 만들지 않을 사진을
        // 미리 받아 두면 목적 없이 보관하는 개인정보가 된다.
        useCampaign(true, false);
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tracking = objectMapper.createObjectNode().put("visitId", "visit-photo-no-slot");
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "figure", tracking, null)
        );
        service.completeSurvey(
                draft.responseId(),
                draft.editToken(),
                new SaveGoodsSurveyDraftRequest(
                        reservableAnswers(),
                        "q33",
                        30_000L,
                        Map.of(),
                        tracking
                )
        );

        assertThatThrownBy(() -> service.createPhotoUpload(
                draft.responseId(),
                draft.editToken(),
                new CreateGoodsSurveyPhotoUploadRequest(
                        UUID.randomUUID().toString(),
                        "image/jpeg",
                        1_024L
                )
        )).hasMessageContaining("현재 설문 상태");
    }

    @Test
    void noticeEmailIsStoredWithoutLinkingItToTheSurveyAnswers() {
        // 설문은 신원 정보를 받지 않고 익명으로 분석한다고 고지하고 받았다.
        // 어떤 응답을 한 사람의 주소인지 남으면 그 고지가 거짓이 된다.
        useCampaign(true, false);
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tracking = objectMapper.createObjectNode().put("visitId", "visit-notice");
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "unselected", tracking, null)
        );
        service.completeSurvey(
                draft.responseId(),
                draft.editToken(),
                new SaveGoodsSurveyDraftRequest(
                        reservableAnswers(),
                        "q33",
                        30_000L,
                        Map.of(),
                        tracking
                )
        );
        when(noticeSubscriptionRepository.existsByEmailHash(any())).thenReturn(false);
        AtomicReference<GoodsSurveyNoticeSubscription> saved = new AtomicReference<>();
        when(noticeSubscriptionRepository.save(any(GoodsSurveyNoticeSubscription.class)))
                .thenAnswer(invocation -> {
                    saved.set(invocation.getArgument(0));
                    return invocation.getArgument(0);
                });

        service.subscribeNotice(
                draft.responseId(),
                draft.editToken(),
                new SubscribeGoodsSurveyNoticeRequest("  Boho@Example.COM ", true)
        );

        // 대소문자와 공백만 다른 주소가 두 번 쌓이지 않도록 맞춰 저장한다.
        assertThat(saved.get().getEmail()).isEqualTo("boho@example.com");
        assertThat(saved.get().getCampaignId()).isEqualTo("goods-2026-07");
        assertThat(saved.get().getUnsubscribedAt()).isNull();
        assertThat(saved.get().getDeleteAfter()).isEqualTo(NOW.plus(365, ChronoUnit.DAYS));
    }

    @Test
    void repeatedNoticeEmailIsAcceptedQuietlyWithoutASecondRow() {
        // 이미 신청됐다고 알려주면 남의 주소가 등록돼 있는지 확인하는 통로가 된다.
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tracking = objectMapper.createObjectNode().put("visitId", "visit-notice-dup");
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "figure", tracking, null)
        );
        service.completeSurvey(
                draft.responseId(),
                draft.editToken(),
                new SaveGoodsSurveyDraftRequest(
                        reservableAnswers(),
                        "q33",
                        30_000L,
                        Map.of(),
                        tracking
                )
        );
        when(noticeSubscriptionRepository.existsByEmailHash(any())).thenReturn(true);

        service.subscribeNotice(
                draft.responseId(),
                draft.editToken(),
                new SubscribeGoodsSurveyNoticeRequest("boho@example.com", true)
        );

        verify(noticeSubscriptionRepository, never())
                .save(any(GoodsSurveyNoticeSubscription.class));
    }

    @Test
    void noticeEmailIsRefusedBeforeTheSurveyIsFinished() {
        // 설문을 마치기 전에는 받지 않는다. 완료 화면에서만 여는 항목이다.
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tracking = objectMapper.createObjectNode().put("visitId", "visit-notice-early");
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "figure", tracking, null)
        );

        assertThatThrownBy(() -> service.subscribeNotice(
                draft.responseId(),
                draft.editToken(),
                new SubscribeGoodsSurveyNoticeRequest("boho@example.com", true)
        )).hasMessageContaining("현재 설문 상태");
    }

    @Test
    void surveyStartsWithoutPickingAGoodsType() {
        // 랜딩에서 굿즈를 고르지 않고 바로 설문에 들어올 수 있다.
        // 예전처럼 아크릴을 자동으로 붙이면 실제 선호가 아닌 값이 집계에 섞인다.
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tracking = objectMapper.createObjectNode().put("visitId", "visit-unselected");

        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "unselected", tracking, null)
        );

        assertThat(draft.responseId()).isNotBlank();
        assertThat(storedResponse.get().getSelectedGoods()).isEqualTo("unselected");
    }

    @Test
    void applicationCannotBeSubmittedWithoutPickingAGoodsType() {
        // 관심 굿즈를 고르지 않은 것과 만들 물건을 정하지 않은 것은 다르다.
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tracking = objectMapper.createObjectNode().put("visitId", "visit-unselected-apply");
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "unselected", tracking, null)
        );
        service.completeSurvey(
                draft.responseId(),
                draft.editToken(),
                new SaveGoodsSurveyDraftRequest(
                        reservableAnswers(),
                        "q33",
                        30_000L,
                        Map.of(),
                        tracking
                )
        );

        assertThatThrownBy(() -> service.submitApplication(
                draft.responseId(),
                draft.editToken(),
                "idempotency-unselected",
                new SubmitGoodsSurveyApplicationRequest(
                        "unselected",
                        "",
                        "몽이",
                        "보호자",
                        "01012345678",
                        null,
                        "01234",
                        "서울시 노원구",
                        "",
                        List.of("photo-unselected"),
                        List.of(),
                        "conversion-unselected",
                        tracking,
                        true,
                        true,
                        false
                )
        )).hasMessageContaining("입력");
    }

    @Test
    void closedSurveySwitchStopsNewResponses() {
        useCampaign(false, false);

        assertThatThrownBy(() -> service.createDraft(
                new CreateGoodsSurveyRequest(
                        "2026-07-25-v2",
                        "figure",
                        new ObjectMapper().createObjectNode().put("visitId", "visit-survey-closed"),
                        null
                )
        )).hasMessageContaining("설문 접수가 종료");
    }

    @Test
    void applicationIsRejectedWhenTheGoodsSwitchGoesDownWhileTheFormIsBeingFilled() {
        // 스위치를 내려도 이미 예약을 받아 둔 사람이 남아 있다.
        // 정원만 확인하면 자리가 남아 있는 한 그 사람들이 그대로 통과한다.
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tracking = objectMapper.createObjectNode().put("visitId", "visit-switch-off");
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "figure", tracking, null)
        );
        service.completeSurvey(
                draft.responseId(),
                draft.editToken(),
                new SaveGoodsSurveyDraftRequest(
                        reservableAnswers(),
                        "q33",
                        30_000L,
                        Map.of(),
                        tracking
                )
        );
        assertThat(storedResponse.get().getStatus())
                .isEqualTo(GoodsSurveyResponseStatus.RESERVED);

        useCampaign(true, false);

        assertThatThrownBy(() -> service.submitApplication(
                draft.responseId(),
                draft.editToken(),
                "idempotency-switch-off",
                new SubmitGoodsSurveyApplicationRequest(
                        "figure",
                        "",
                        "몽이",
                        "보호자",
                        "01012345678",
                        null,
                        "01234",
                        "서울시 노원구",
                        "",
                        List.of("photo-switch-off"),
                        List.of(),
                        "conversion-switch-off",
                        tracking,
                        true,
                        true,
                        false
                )
        )).hasMessageContaining("굿즈 신청이 마감");
    }

    @Test
    void completedSurveyKeepsEverySlotUntilTheApplicationIsSubmitted() {
        JsonNode tracking = new ObjectMapper().valueToTree(Map.of(
                "visitId", "visit-1",
                "device", Map.of("category", "mobile")
        ));
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "figure", tracking, null)
        );

        GoodsSurveyCompletionResponse result = service.completeSurvey(
                draft.responseId(),
                draft.editToken(),
                new SaveGoodsSurveyDraftRequest(
                        reservableAnswers(),
                        "q33",
                        120_000L,
                        Map.of("q1", 3_000L),
                        tracking
                )
        );

        assertThat(result.status()).isEqualTo("RESERVED");
        // 노션 기준: 설문만 끝낸 예약은 자리를 잡아두지 않는다.
        assertThat(result.remaining()).isEqualTo(73);
        assertThat(result.reservationExpiresAt()).isEqualTo(NOW.plusSeconds(15 * 60));
        assertThat(storedResponse.get().getStatus()).isEqualTo(GoodsSurveyResponseStatus.RESERVED);
        assertThat(storedResponse.get().getAnswersJson()).contains("\"q1\":\"current_only\"");
        assertThat(storedResponse.get().getTrackingJson()).doesNotContain("guardianName");
    }

    @Test
    void fullCampaignStoresTheSurveyButNeverOpensThePersonalInformationStep() {
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest(
                        "2026-07-23-v1",
                        "figure",
                        new ObjectMapper().createObjectNode().put("visitId", "visit-2"),
                        null
                )
        );
        when(responseRepository.countSubmittedAllocations(any(), any(), any()))
                .thenReturn(73L);

        GoodsSurveyCompletionResponse result = service.completeSurvey(
                draft.responseId(),
                draft.editToken(),
                new SaveGoodsSurveyDraftRequest(
                        reservableAnswers(),
                        "q33",
                        90_000L,
                        Map.of(),
                        new ObjectMapper().createObjectNode().put("visitId", "visit-2")
                )
        );

        assertThat(result.status()).isEqualTo("COMPLETED_NO_SLOT");
        assertThat(result.remaining()).isZero();
        assertThat(result.reservationExpiresAt()).isNull();
        assertThat(storedResponse.get().getStatus())
                .isEqualTo(GoodsSurveyResponseStatus.COMPLETED_NO_SLOT);
    }

    @Test
    void arbitraryOptionIdsCannotReserveARewardSlot() {
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest(
                        "2026-07-25-v2",
                        "figure",
                        new ObjectMapper().createObjectNode().put("visitId", "visit-3"),
                        null
                )
        );

        assertThatThrownBy(() -> service.completeSurvey(
                draft.responseId(),
                draft.editToken(),
                new SaveGoodsSurveyDraftRequest(
                        Map.of(
                                "q1", new ObjectMapper().getNodeFactory().textNode("current_only"),
                                "q3", new ObjectMapper().getNodeFactory().textNode("not-a-real-option")
                        ),
                        "q3",
                        10_000L,
                        Map.of(),
                        new ObjectMapper().createObjectNode().put("visitId", "visit-3")
                )
        )).hasMessageContaining("설문 응답 형식");
    }

    @Test
    void nonTerminatingCompletionWithoutEnoughAnswersCannotReserveASlot() {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tracking = objectMapper.createObjectNode().put("visitId", "visit-thin");
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "figure", tracking, null)
        );

        assertThatThrownBy(() -> service.completeSurvey(
                draft.responseId(),
                draft.editToken(),
                new SaveGoodsSurveyDraftRequest(
                        Map.of("q1", objectMapper.getNodeFactory().textNode("current_only")),
                        "q1",
                        4_000L,
                        Map.of(),
                        tracking
                )
        )).hasMessageContaining("최소 설문 응답 수");

        assertThat(storedResponse.get().getStatus())
                .isEqualTo(GoodsSurveyResponseStatus.DRAFT);
    }

    @Test
    void 사진이_세_장보다_적으면_접수되지_않는다() {
        // 근거: [카톡 나혜님] "사진 3개 이상 등록해야 제출 버튼 활성화되도록
        //       변경해주세요. 즉, 사진 3개 이상만 제출 가능하도록 (3-5개)"
        //
        // 화면은 세 장부터 열리게 고쳤지만 API 는 한 장도 받고 있었다. 화면만
        // 막으면 그 화면을 거치지 않는 요청이 그대로 들어온다. 얼굴·전신·털무늬
        // 세 종이 제작의 최소 구성이라, 두 장짜리 주문은 만들 수 없다.
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tracking = objectMapper.createObjectNode().put("visitId", "visit-too-few");
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "figure", tracking, null)
        );
        service.completeSurvey(
                draft.responseId(),
                draft.editToken(),
                new SaveGoodsSurveyDraftRequest(
                        reservableAnswers(),
                        "q33",
                        30_000L,
                        Map.of(),
                        tracking
                )
        );

        // 두 장이 실제로 올라와 확인까지 끝난 상태를 만든다. 그래야 "덜
        // 올라왔다"가 아니라 "장수가 모자라다"로 걸리는지 볼 수 있다.
        // 고치고 나면 장수를 먼저 보고 끊으므로 이 stub 은 쓰이지 않는다.
        lenient().when(photoRepository.findAllByIdInAndResponseIdAndStatus(
                any(), any(), any()
        )).thenReturn(List.of(
                confirmedPhoto("photo-1", draft.responseId()),
                confirmedPhoto("photo-2", draft.responseId())
        ));

        assertThatThrownBy(() -> service.submitApplication(
                draft.responseId(),
                draft.editToken(),
                "idempotency-too-few",
                new SubmitGoodsSurveyApplicationRequest(
                        "figure",
                        "",
                        "몽이",
                        "보호자",
                        "01012345678",
                        null,
                        "01234",
                        "서울시 노원구",
                        "",
                        List.of("photo-1", "photo-2"),
                        List.of(),
                        "conversion-too-few",
                        tracking,
                        true,
                        true,
                        false
                )
        )).hasMessageContaining("3장");
    }

    @Test
    void applicationStoresPublicationConsentForEachConfirmedPhoto() {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tracking = objectMapper.createObjectNode().put("visitId", "visit-photo");
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "figure", tracking, null)
        );
        service.completeSurvey(
                draft.responseId(),
                draft.editToken(),
                new SaveGoodsSurveyDraftRequest(
                        reservableAnswers(),
                        "q33",
                        30_000L,
                        Map.of(),
                        tracking
                )
        );

        GoodsSurveyPhoto publicPhoto = confirmedPhoto("photo-public", draft.responseId());
        GoodsSurveyPhoto privatePhoto = confirmedPhoto("photo-private", draft.responseId());
        GoodsSurveyPhoto anotherPrivate = confirmedPhoto("photo-private-2", draft.responseId());
        when(photoRepository.findAllByIdInAndResponseIdAndStatus(
                any(), any(), any()
        )).thenReturn(List.of(publicPhoto, privatePhoto, anotherPrivate));

        service.submitApplication(
                draft.responseId(),
                draft.editToken(),
                "idempotency-photo-consent",
                new SubmitGoodsSurveyApplicationRequest(
                        "figure",
                        "",
                        "몽이",
                        "보호자",
                        "01012345678",
                        null,
                        "01234",
                        "서울시 노원구",
                        "",
                        List.of("photo-public", "photo-private", "photo-private-2"),
                        List.of("photo-public"),
                        "conversion-photo-consent",
                        tracking,
                        true,
                        true,
                        false
                )
        );

        assertThat(publicPhoto.isPublicationAgreed()).isTrue();
        assertThat(privatePhoto.isPublicationAgreed()).isFalse();
        assertThat(anotherPrivate.isPublicationAgreed()).isFalse();
    }

    @Test
    void 접수되면_팀에_알릴_거리를_내보낸다() {
        // 팀 채널이 이 값을 보고 움직인다. 여기서 빠진 항목은 어드민을
        // 열어야만 보인다.
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tracking = objectMapper.readTree(
                "{\"visitId\":\"visit-notify\","
                        + "\"lastTouch\":{\"utm_source\":\"instagram\",\"utm_medium\":\"cpc\"}}"
        );
        submitOnce(tracking, "idempotency-notify", "몽이", "황성욱", "01012345678");

        GoodsOrderSubmittedEvent event = (GoodsOrderSubmittedEvent) publishedEvents.stream()
                .filter(GoodsOrderSubmittedEvent.class::isInstance)
                .findFirst()
                .orElseThrow();

        assertThat(event.guardianName()).isEqualTo("황성욱");
        assertThat(event.phone()).isEqualTo("01012345678");
        assertThat(event.petName()).isEqualTo("몽이");
        // 식별자가 아니라 사람이 읽는 이름이어야 한다.
        assertThat(event.goodsLabel()).isEqualTo("3D 전신 피규어");
        assertThat(event.trafficSource()).isEqualTo("instagram / cpc");
        assertThat(event.orderNumber()).isNotBlank();
        assertThat(event.surveyParticipant()).isTrue();
    }

    @Test
    void 접수가_거절되면_아무것도_알리지_않는다() {
        // 정원이 찬 뒤에도 알림이 나가면, 받지 않은 주문을 팀이 처리하러 간다.
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tracking = objectMapper.createObjectNode().put("visitId", "visit-full");
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "figure", tracking, null)
        );
        service.completeSurvey(
                draft.responseId(),
                draft.editToken(),
                new SaveGoodsSurveyDraftRequest(reservableAnswers(), "q33", 30_000L, Map.of(), tracking)
        );
        useCampaign(true, false);

        assertThatThrownBy(() -> service.submitApplication(
                draft.responseId(),
                draft.editToken(),
                "idempotency-full",
                applicationRequest(tracking, "몽이", "황성욱", "01012345678")
        )).isInstanceOf(Exception.class);

        assertThat(publishedEvents).noneMatch(GoodsOrderSubmittedEvent.class::isInstance);
    }

    private void submitOnce(
            JsonNode tracking,
            String idempotencyKey,
            String petName,
            String guardianName,
            String phone
    ) {
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "figure", tracking, null)
        );
        service.completeSurvey(
                draft.responseId(),
                draft.editToken(),
                new SaveGoodsSurveyDraftRequest(reservableAnswers(), "q33", 30_000L, Map.of(), tracking)
        );
        when(photoRepository.findAllByIdInAndResponseIdAndStatus(any(), any(), any()))
                .thenReturn(confirmedPhotos("photo", draft.responseId()));
        service.submitApplication(
                draft.responseId(),
                draft.editToken(),
                idempotencyKey,
                applicationRequest(tracking, petName, guardianName, phone)
        );
    }

    private SubmitGoodsSurveyApplicationRequest applicationRequest(
            JsonNode tracking,
            String petName,
            String guardianName,
            String phone
    ) {
        return new SubmitGoodsSurveyApplicationRequest(
                "figure",
                "",
                petName,
                guardianName,
                phone,
                null,
                "01234",
                "서울시 노원구",
                "",
                photoIds("photo"),
                List.of(),
                "conversion-notify",
                tracking,
                true,
                true,
                false
        );
    }

    @Test
    void applicationIsRejectedWhenTheLastSlotIsTakenWhileTheFormIsBeingFilled() {
        // 정원은 제출 시점에 확정한다. 이 확인이 없으면 정원을 넘겨 접수된다.
        // 다 채우고 나서 거절당하지 않도록, 프런트가 이어서 진행할 때
        // 캠페인 상태를 먼저 확인해 마감 화면을 앞에서 보여준다.
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tracking = objectMapper.createObjectNode().put("visitId", "visit-late");
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "figure", tracking, null)
        );
        service.completeSurvey(
                draft.responseId(),
                draft.editToken(),
                new SaveGoodsSurveyDraftRequest(
                        reservableAnswers(),
                        "q33",
                        30_000L,
                        Map.of(),
                        tracking
                )
        );

        // 배송 정보를 적는 동안 남은 자리가 모두 나갔다.
        when(responseRepository.countSubmittedAllocations(any(), any(), any()))
                .thenReturn(73L);

        assertThatThrownBy(() -> service.submitApplication(
                draft.responseId(),
                draft.editToken(),
                "idempotency-late",
                new SubmitGoodsSurveyApplicationRequest(
                        "figure",
                        "",
                        "몽이",
                        "보호자",
                        "01012345678",
                        null,
                        "01234",
                        "서울시 노원구",
                        "",
                        List.of("photo-late"),
                        List.of(),
                        "conversion-late",
                        tracking,
                        true,
                        true,
                        false
                )
        )).hasMessageContaining("굿즈 신청이 마감");

        assertThat(storedResponse.get().getStatus())
                .isEqualTo(GoodsSurveyResponseStatus.RESERVED);
    }

    @Test
    void applicationStillGoesThroughAfterTheReservationWindowHasPassed() {
        // 사연을 쓰고 주소를 찾고 사진을 올리다 보면 15분은 쉽게 넘는다.
        // 예약은 더 이상 자리를 잡아두지 않으므로 시간이 지났다고 막으면
        // 설문을 다 끝낸 사람이 마지막 단계에서 통째로 잃는다.
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tracking = objectMapper.createObjectNode().put("visitId", "visit-slow");
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "figure", tracking, null)
        );
        service.completeSurvey(
                draft.responseId(),
                draft.editToken(),
                new SaveGoodsSurveyDraftRequest(
                        reservableAnswers(),
                        "q33",
                        30_000L,
                        Map.of(),
                        tracking
                )
        );

        // 예약 15분을 훌쩍 넘긴 시점.
        currentTime = NOW.plusSeconds(90 * 60);

        when(photoRepository.findAllByIdInAndResponseIdAndStatus(
                any(), any(), any()
        )).thenReturn(confirmedPhotos("photo-slow", draft.responseId()));

        service.submitApplication(
                draft.responseId(),
                draft.editToken(),
                "idempotency-slow",
                new SubmitGoodsSurveyApplicationRequest(
                        "figure",
                        "",
                        "몽이",
                        "보호자",
                        "01012345678",
                        null,
                        "01234",
                        "서울시 노원구",
                        "",
                        photoIds("photo-slow"),
                        List.of(),
                        "conversion-slow",
                        tracking,
                        true,
                        true,
                        false
                )
        );

        assertThat(storedResponse.get().getStatus())
                .isEqualTo(GoodsSurveyResponseStatus.SUBMITTED);
    }

    @Test
    void legacyApplicationWithoutPhotoPublicationConsentKeepsEveryPhotoPrivate() {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tracking = objectMapper.createObjectNode().put("visitId", "visit-legacy-photo");
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-23-v1", "figure", tracking, null)
        );
        service.completeSurvey(
                draft.responseId(),
                draft.editToken(),
                new SaveGoodsSurveyDraftRequest(
                        reservableAnswers(),
                        "q33",
                        30_000L,
                        Map.of(),
                        tracking
                )
        );

        List<GoodsSurveyPhoto> legacyPhotos =
                confirmedPhotos("photo-legacy-private", draft.responseId());
        when(photoRepository.findAllByIdInAndResponseIdAndStatus(
                any(), any(), any()
        )).thenReturn(legacyPhotos);

        service.submitApplication(
                draft.responseId(),
                draft.editToken(),
                "idempotency-legacy-photo",
                new SubmitGoodsSurveyApplicationRequest(
                        "figure",
                        "",
                        "몽이",
                        "보호자",
                        "01012345678",
                        null,
                        "01234",
                        "서울시 노원구",
                        "",
                        photoIds("photo-legacy-private"),
                        null,
                        "conversion-legacy-photo",
                        tracking,
                        true,
                        true,
                        false
                )
        );

        assertThat(legacyPhotos).noneMatch(GoodsSurveyPhoto::isPublicationAgreed);
    }

    private static Map<String, JsonNode> reservableAnswers() {
        ObjectMapper mapper = new ObjectMapper();
        return Map.of(
                "q1", mapper.getNodeFactory().textNode("current_only"),
                "q2", mapper.getNodeFactory().textNode("current"),
                "q3", mapper.getNodeFactory().textNode("3"),
                "q4", mapper.getNodeFactory().arrayNode().add("healthy"),
                "q5", mapper.getNodeFactory().textNode("2"),
                "q6", mapper.getNodeFactory().textNode("1")
        );
    }

    @Test
    void 설문을_건너뛰고_신청하면_정가가_적용된다() {
        // 설문에 답하는 대신 값을 더 내는 길이다. 여기서 할인가가 적용되면
        // 설문을 끝까지 답할 이유가 사라진다.
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tracking = objectMapper.createObjectNode().put("visitId", "visit-direct");
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "figure", tracking, null)
        );

        service.startDirectPurchase(draft.responseId(), draft.editToken());

        when(photoRepository.findAllByIdInAndResponseIdAndStatus(any(), any(), any()))
                .thenReturn(confirmedPhotos("photo-direct", draft.responseId()));

        ArgumentCaptor<GoodsSurveyFulfillment> saved =
                ArgumentCaptor.forClass(GoodsSurveyFulfillment.class);
        service.submitApplication(
                draft.responseId(),
                draft.editToken(),
                "idempotency-direct",
                directApplication(tracking, "conversion-direct", "photo-direct")
        );

        verify(fulfillmentRepository).save(saved.capture());
        assertThat(saved.getValue().isSurveyParticipant()).isFalse();
        // 정상가 그대로. 깎아 줄 근거가 없다.
        // 제작비 29,900 + 배송비 3,000
        assertThat(saved.getValue().getPaymentAmountKrw()).isEqualTo(32_900);
        assertThat(saved.getValue().getShippingFeeKrw()).isEqualTo(3_000);
        assertThat(saved.getValue().getDiscountAmountKrw()).isZero();
        assertThat(saved.getValue().getPromotionName()).isNull();
    }

    @Test
    void 설문을_마치고_신청하면_할인가가_적용된다() {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tracking = objectMapper.createObjectNode().put("visitId", "visit-member");
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "figure", tracking, null)
        );
        service.completeSurvey(
                draft.responseId(),
                draft.editToken(),
                new SaveGoodsSurveyDraftRequest(
                        reservableAnswers(),
                        "q33",
                        30_000L,
                        Map.of(),
                        tracking
                )
        );

        when(photoRepository.findAllByIdInAndResponseIdAndStatus(any(), any(), any()))
                .thenReturn(confirmedPhotos("photo-member", draft.responseId()));

        ArgumentCaptor<GoodsSurveyFulfillment> saved =
                ArgumentCaptor.forClass(GoodsSurveyFulfillment.class);
        service.submitApplication(
                draft.responseId(),
                draft.editToken(),
                "idempotency-member",
                directApplication(tracking, "conversion-member", "photo-member")
        );

        verify(fulfillmentRepository).save(saved.capture());
        assertThat(saved.getValue().isSurveyParticipant()).isTrue();
        // 제작비 23,900 + 배송비 3,000
        assertThat(saved.getValue().getPaymentAmountKrw()).isEqualTo(26_900);
        assertThat(saved.getValue().getShippingFeeKrw()).isEqualTo(3_000);
        assertThat(saved.getValue().getDiscountAmountKrw()).isEqualTo(6_000);
        assertThat(saved.getValue().getPromotionName()).isEqualTo("설문 참여 할인");
    }

    private SubmitGoodsSurveyApplicationRequest directApplication(
            JsonNode tracking,
            String conversionEventId,
            String photoPrefix
    ) {
        return new SubmitGoodsSurveyApplicationRequest(
                "figure",
                "",
                "몽이",
                "보호자",
                "01012345678",
                null,
                "01234",
                "서울시 노원구",
                "",
                photoIds(photoPrefix),
                List.of(),
                conversionEventId,
                tracking,
                true,
                true,
                false
        );
    }

    /**
     * 제작에 필요한 최소 구성인 세 장.
     *
     * 장수가 모자란 신청은 접수되지 않으므로, 성공 경로를 다루는 시험은 세
     * 장을 갖춰 둔다.
     */
    private List<String> photoIds(String prefix) {
        return List.of(prefix + "-1", prefix + "-2", prefix + "-3");
    }

    private List<GoodsSurveyPhoto> confirmedPhotos(String prefix, String responseId) {
        return photoIds(prefix).stream()
                .map(id -> confirmedPhoto(id, responseId))
                .toList();
    }

    private GoodsSurveyPhoto confirmedPhoto(String id, String responseId) {
        GoodsSurveyPhoto photo = GoodsSurveyPhoto.pending(
                id,
                responseId,
                "client-" + id,
                "goods-survey/" + responseId + "/" + id + ".jpg",
                "image/jpeg",
                1024,
                NOW.plusSeconds(600)
        );
        photo.confirm(1024, NOW);
        return photo;
    }
}
