package com.pawever.backend.goodssurvey.service;

import com.pawever.backend.global.security.HmacHasher;
import com.pawever.backend.goodssurvey.config.GoodsSurveyProperties;
import com.pawever.backend.goodssurvey.dto.CreateGoodsSurveyRequest;
import com.pawever.backend.goodssurvey.dto.GoodsSurveyCompletionResponse;
import com.pawever.backend.goodssurvey.dto.GoodsSurveyDraftResponse;
import com.pawever.backend.goodssurvey.dto.SaveGoodsSurveyDraftRequest;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyCampaign;
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
        when(responseRepository.countActiveAllocations(
                any(), any(), any(), any()
        )).thenReturn(0L);

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
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void completedSurveyReservesOneOfTheRealRemainingSlots() {
        JsonNode tracking = new ObjectMapper().valueToTree(Map.of(
                "visitId", "visit-1",
                "device", Map.of("category", "mobile")
        ));
        GoodsSurveyDraftResponse draft = service.createDraft(
                new CreateGoodsSurveyRequest("2026-07-23-v1", "acrylic", tracking)
        );

        GoodsSurveyCompletionResponse result = service.completeSurvey(
                draft.responseId(),
                draft.editToken(),
                new SaveGoodsSurveyDraftRequest(
                        Map.of("q1", new ObjectMapper().getNodeFactory().textNode("current_only")),
                        "q33",
                        120_000L,
                        Map.of("q1", 3_000L),
                        tracking
                )
        );

        assertThat(result.status()).isEqualTo("RESERVED");
        assertThat(result.remaining()).isEqualTo(72);
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
        when(responseRepository.countActiveAllocations(
                any(), any(), any(), any()
        )).thenReturn(73L);

        GoodsSurveyCompletionResponse result = service.completeSurvey(
                draft.responseId(),
                draft.editToken(),
                new SaveGoodsSurveyDraftRequest(
                        Map.of("q1", new ObjectMapper().getNodeFactory().textNode("current_only")),
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
                        "2026-07-23-v1",
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
}
