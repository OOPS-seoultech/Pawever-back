package com.pawever.backend.goodssurvey.service;

import com.pawever.backend.global.security.HmacHasher;
import com.pawever.backend.goodssurvey.config.GoodsSurveyProperties;
import com.pawever.backend.goodssurvey.dto.CreateGoodsSurveyRequest;
import com.pawever.backend.goodssurvey.dto.GoodsSurveyCompletionResponse;
import com.pawever.backend.goodssurvey.dto.GoodsSurveyDraftResponse;
import com.pawever.backend.goodssurvey.dto.SaveGoodsSurveyDraftRequest;
import com.pawever.backend.goodssurvey.dto.SubmitGoodsSurveyApplicationRequest;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyCampaign;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyPhoto;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyResponse;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyResponseStatus;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyCampaignRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyFulfillmentRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyPhotoRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyResponseRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyStoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoodsSurveyServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-24T09:00:00Z");

    @Mock private GoodsSurveyCampaignRepository campaignRepository;
    @Mock private GoodsSurveyResponseRepository responseRepository;
    @Mock private GoodsSurveyStoryRepository storyRepository;
    @Mock private GoodsSurveyFulfillmentRepository fulfillmentRepository;
    @Mock private GoodsSurveyPhotoRepository photoRepository;
    @Mock private GoodsSurveyPhotoStorage photoStorage;

    private GoodsSurveyService service;
    private GoodsSurveyCampaign campaign;
    // 예약 만료 같은 시간 의존 동작을 재현하려면 시계를 움직일 수 있어야 한다.
    private Instant currentTime = NOW;
    private final AtomicReference<GoodsSurveyResponse> storedResponse = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        GoodsSurveyProperties properties = new GoodsSurveyProperties();
        properties.setCampaignId("goods-2026-07");
        properties.setReservationMinutes(15);

        campaign = GoodsSurveyCampaign.create(
                "goods-2026-07",
                100,
                27,
                Instant.parse("2026-07-23T00:00:00Z"),
                Instant.parse("2026-08-05T14:59:59Z")
        );

        when(campaignRepository.findById("goods-2026-07"))
                .thenReturn(Optional.of(campaign));
        lenient().when(campaignRepository.findByIdForUpdate("goods-2026-07"))
                .thenReturn(Optional.of(campaign));
        when(responseRepository.save(any(GoodsSurveyResponse.class)))
                .thenAnswer(invocation -> {
                    GoodsSurveyResponse response = invocation.getArgument(0);
                    storedResponse.set(response);
                    return response;
                });
        when(responseRepository.findById(any()))
                .thenAnswer(invocation -> Optional.ofNullable(storedResponse.get()));
        when(responseRepository.countSubmittedAllocations(any(), any()))
                .thenReturn(0L);

        service = new GoodsSurveyService(
                campaignRepository,
                responseRepository,
                storyRepository,
                fulfillmentRepository,
                photoRepository,
                photoStorage,
                new GoodsSurveyAnswerValidator(new ObjectMapper()),
                new ObjectMapper(),
                new HmacHasher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="),
                properties,
                new Clock() {
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
                }
        );
    }

    @Test
    void completedSurveyKeepsEverySlotUntilTheApplicationIsSubmitted() {
        JsonNode tracking = new ObjectMapper().valueToTree(Map.of(
                "visitId", "visit-1",
                "device", Map.of("category", "mobile")
        ));
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "acrylic", tracking)
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
                        "face",
                        new ObjectMapper().createObjectNode().put("visitId", "visit-2")
                )
        );
        when(responseRepository.countSubmittedAllocations(any(), any()))
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
                        new ObjectMapper().createObjectNode().put("visitId", "visit-3")
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
                new CreateGoodsSurveyRequest("2026-07-25-v2", "acrylic", tracking)
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
    void applicationStoresPublicationConsentForEachConfirmedPhoto() {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tracking = objectMapper.createObjectNode().put("visitId", "visit-photo");
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "acrylic", tracking)
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
        when(photoRepository.findAllByIdInAndResponseIdAndStatus(
                any(), any(), any()
        )).thenReturn(List.of(publicPhoto, privatePhoto));

        service.submitApplication(
                draft.responseId(),
                draft.editToken(),
                "idempotency-photo-consent",
                new SubmitGoodsSurveyApplicationRequest(
                        "acrylic",
                        "",
                        "몽이",
                        "보호자",
                        "01012345678",
                        "01234",
                        "서울시 노원구",
                        "",
                        List.of("photo-public", "photo-private"),
                        List.of("photo-public"),
                        "conversion-photo-consent",
                        tracking,
                        true,
                        true
                )
        );

        assertThat(publicPhoto.isPublicationAgreed()).isTrue();
        assertThat(privatePhoto.isPublicationAgreed()).isFalse();
    }

    @Test
    void applicationIsRejectedWhenTheLastSlotIsTakenWhileTheFormIsBeingFilled() {
        // 예약이 자리를 잡아두지 않으므로 마감 판정은 제출 시점에 해야 한다.
        // 이 확인이 없으면 정원을 넘겨 접수된다.
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tracking = objectMapper.createObjectNode().put("visitId", "visit-late");
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-25-v2", "acrylic", tracking)
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
        when(responseRepository.countSubmittedAllocations(any(), any()))
                .thenReturn(73L);

        assertThatThrownBy(() -> service.submitApplication(
                draft.responseId(),
                draft.editToken(),
                "idempotency-late",
                new SubmitGoodsSurveyApplicationRequest(
                        "acrylic",
                        "",
                        "몽이",
                        "보호자",
                        "01012345678",
                        "01234",
                        "서울시 노원구",
                        "",
                        List.of("photo-late"),
                        List.of(),
                        "conversion-late",
                        tracking,
                        true,
                        true
                )
        )).hasMessageContaining("선착순 모집이 마감");

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
                new CreateGoodsSurveyRequest("2026-07-25-v2", "acrylic", tracking)
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

        GoodsSurveyPhoto photo = confirmedPhoto("photo-slow", draft.responseId());
        when(photoRepository.findAllByIdInAndResponseIdAndStatus(
                any(), any(), any()
        )).thenReturn(List.of(photo));

        service.submitApplication(
                draft.responseId(),
                draft.editToken(),
                "idempotency-slow",
                new SubmitGoodsSurveyApplicationRequest(
                        "acrylic",
                        "",
                        "몽이",
                        "보호자",
                        "01012345678",
                        "01234",
                        "서울시 노원구",
                        "",
                        List.of("photo-slow"),
                        List.of(),
                        "conversion-slow",
                        tracking,
                        true,
                        true
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
                new CreateGoodsSurveyRequest("2026-07-23-v1", "face", tracking)
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

        GoodsSurveyPhoto privatePhoto = confirmedPhoto("photo-legacy-private", draft.responseId());
        when(photoRepository.findAllByIdInAndResponseIdAndStatus(
                any(), any(), any()
        )).thenReturn(List.of(privatePhoto));

        service.submitApplication(
                draft.responseId(),
                draft.editToken(),
                "idempotency-legacy-photo",
                new SubmitGoodsSurveyApplicationRequest(
                        "face",
                        "",
                        "몽이",
                        "보호자",
                        "01012345678",
                        "01234",
                        "서울시 노원구",
                        "",
                        List.of("photo-legacy-private"),
                        null,
                        "conversion-legacy-photo",
                        tracking,
                        true,
                        true
                )
        );

        assertThat(privatePhoto.isPublicationAgreed()).isFalse();
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
