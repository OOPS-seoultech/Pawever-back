package com.pawever.backend.goodssurvey.controller;

import com.pawever.backend.goodssurvey.entity.GoodsSalesChannel;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyCampaign;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyPhoto;
import com.pawever.backend.goodssurvey.repository.GoodsOrderPetRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyCampaignRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyFulfillmentRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyPhotoRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyResponseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.cors.allowed-origins=https://pawever-landing.pages.dev,https://feat-goods-survey-landing.pawever-landing.pages.dev",
        "survey.goods.flea-campaign-id=goods-2026-09-flea"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoodsSurveyControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private GoodsSurveyCampaignRepository campaignRepository;
    @Autowired private GoodsSurveyResponseRepository responseRepository;
    @Autowired private GoodsSurveyPhotoRepository photoRepository;
    @Autowired private GoodsSurveyFulfillmentRepository fulfillmentRepository;
    @Autowired private GoodsOrderPetRepository petRepository;

    @BeforeEach
    void setUp() {
        petRepository.deleteAll();
        fulfillmentRepository.deleteAll();
        photoRepository.deleteAll();
        responseRepository.deleteAll();
        campaignRepository.deleteAll();
        Instant now = Instant.now();
        campaignRepository.save(
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
        campaignRepository.save(
                GoodsSurveyCampaign.create(
                        "goods-2026-09-flea",
                        GoodsSalesChannel.FLEA,
                        70,
                        0,
                        now.minusSeconds(3600),
                        now.plusSeconds(3600),
                        false,
                        true
                )
        );
    }

    @Test
    void 플리마켓_랜딩은_자기_모집의_남은_자리를_묻는다() throws Exception {
        // 온라인과 현장은 정원을 따로 센다. 경로를 적지 않으면 상시 온라인,
        // flea 를 적으면 플리마켓 모집을 본다.
        mockMvc.perform(get("/api/public/goods-survey/campaign"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channel").value("ONLINE"))
                .andExpect(jsonPath("$.data.capacity").value(100));

        mockMvc.perform(get("/api/public/goods-survey/campaign").param("channel", "flea"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channel").value("FLEA"))
                .andExpect(jsonPath("$.data.campaignId").value("goods-2026-09-flea"))
                .andExpect(jsonPath("$.data.capacity").value(70))
                .andExpect(jsonPath("$.data.remaining").value(70))
                // 설문은 닫혀 있고 굿즈만 열려 있다. QR 을 찍고 바로 주문한다.
                .andExpect(jsonPath("$.data.surveyOpen").value(false))
                .andExpect(jsonPath("$.data.goodsOpen").value(true));
    }

    @Test
    void 플리마켓으로_들어오면_그_모집에_붙는다() throws Exception {
        // 설문 스위치가 닫혀 있어도 플리마켓 주문은 만들어져야 한다. 여기서
        // 막히면 현장에서 QR 을 찍은 사람이 아무것도 못 한다.
        String createBody = """
                {
                  "questionnaireVersion": "2026-07-25-v2",
                  "selectedGoods": "figure",
                  "tracking": {"visitId": "visit-flea", "device": {"category": "mobile"}},
                  "channel": "flea"
                }
                """;
        String created = mockMvc.perform(
                        post("/api/public/goods-survey/responses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remaining").value(70))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String responseId = objectMapper.readTree(created).path("data").path("responseId").asText();
        assertThat(responseRepository.findById(responseId))
                .get()
                .extracting(response -> response.getCampaignId())
                .isEqualTo("goods-2026-09-flea");
    }

    @Test
    void anonymousBrowserCanCreateAndCompleteAnInternalSurvey() throws Exception {
        String createBody = """
                {
                  "questionnaireVersion": "2026-07-25-v2",
                  "selectedGoods": "figure",
                  "tracking": {
                    "visitId": "visit-integration",
                    "device": {"category": "mobile"}
                  }
                }
                """;
        String createResponse = mockMvc.perform(
                        post("/api/public/goods-survey/responses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.remaining").value(73))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode draft = objectMapper.readTree(createResponse).path("data");
        String responseId = draft.path("responseId").asText();
        String editToken = draft.path("editToken").asText();

        String completeBody = """
                {
                  "answers": {"q1": "current_only", "q2": "current", "q3": "3", "q4": ["healthy"], "q5": "2", "q6": "1"},
                  "currentQuestionId": "q6",
                  "surveyActiveMs": 15000,
                  "questionActiveMs": {"q1": 3000},
                  "tracking": {
                    "visitId": "visit-integration",
                    "conversionEventId": "event-integration",
                    "device": {"category": "mobile"}
                  }
                }
                """;
        mockMvc.perform(
                        post("/api/public/goods-survey/responses/{responseId}/complete", responseId)
                                .header("X-Survey-Edit-Token", editToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(completeBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("RESERVED"))
                // 설문만 끝낸 예약은 자리를 잡아두지 않으므로 남은 자리가 그대로다.
                // 이 테스트가 만든 캠페인은 정원 100, 과거 배정 27이라 73이 남는다.
                .andExpect(jsonPath("$.data.remaining").value(73))
                .andExpect(jsonPath("$.data.reservationExpiresAt").isNotEmpty());
    }

    @Test
    void cloudflarePagesCanPreflightGoodsSurveyRequests() throws Exception {
        String origin = "https://feat-goods-survey-landing.pawever-landing.pages.dev";

        mockMvc.perform(
                        options("/api/public/goods-survey/responses")
                                .header(HttpHeaders.ORIGIN, origin)
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                        "content-type,x-survey-edit-token,idempotency-key"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        containsString("POST")
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString("idempotency-key")
                ));
    }

    @Test
    void untrustedOriginCannotPreflightGoodsSurveyRequests() throws Exception {
        mockMvc.perform(
                        options("/api/public/goods-survey/responses")
                                .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    /**
     * 화면이 보내는 신청서를 문자열 그대로 보낸다.
     *
     * 여러 마리 신청은 지금까지 서비스 단위 검사가 요청 객체를 자바로 직접
     * 만들어 넣는 방식으로만 봤다. 그래서 JSON 을 객체로 읽는 층이 통째로
     * 빠져 있었고, 2026-09-16 에 화면이 원시 boolean 하나를 빼자 엿새 동안
     * 주문이 한 건도 접수되지 않는데도 검사는 내내 초록이었다.
     */
    @Test
    void 신청_폼이_보내는_그대로_주문이_접수된다() throws Exception {
        Draft draft = fleaDirectPurchase();
        List<String> photoIds = confirmedPhotos(draft.responseId(), 3);

        mockMvc.perform(
                        post("/api/public/goods-survey/responses/{responseId}/application", draft.responseId())
                                .header("X-Survey-Edit-Token", draft.editToken())
                                .header("Idempotency-Key", "idempotency-flea-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(applicationBody(photoIds, true, true))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.orderNumber").isNotEmpty())
                // 제작비 18,900 + 배송비 3,000. 돈이 걸린 값이라 같이 고정한다.
                .andExpect(jsonPath("$.data.paymentAmountKrw").value(21900));
    }

    /**
     * 원시 플래그가 빠진 신청서.
     *
     * record 의 boolean 은 "값 없음"을 담지 못해, 빠지면 본문을 읽는 단계에서
     * 요청이 통째로 거절된다. 그러면 우리가 적어 둔 검증은 한 줄도 돌지 않고
     * 답에도 무엇이 문제인지 남지 않는다 - 화면 한 곳이 항목을 옮기다 빠뜨린
     * 것만으로 접수가 0 이 된다. 빠진 값은 false 로 읽는다.
     */
    @Test
    void 원시_플래그가_빠져도_주문이_접수된다() throws Exception {
        Draft draft = fleaDirectPurchase();
        List<String> photoIds = confirmedPhotos(draft.responseId(), 3);

        mockMvc.perform(
                        post("/api/public/goods-survey/responses/{responseId}/application", draft.responseId())
                                .header("X-Survey-Edit-Token", draft.editToken())
                                .header("Idempotency-Key", "idempotency-flea-2")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(applicationBody(photoIds, false, true))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNumber").isNotEmpty());
    }

    /**
     * 동의는 느슨하게 읽어도 뚫리지 않는다.
     *
     * 빠진 값을 false 로 읽는 완화가 동의까지 풀어 주면, 동의한 적 없는 사람의
     * 주문이 생긴다. 빠진 동의는 false 가 되고 그 자리에서 거절되어야 한다.
     */
    @Test
    void 동의가_빠지면_주문이_접수되지_않는다() throws Exception {
        Draft draft = fleaDirectPurchase();
        List<String> photoIds = confirmedPhotos(draft.responseId(), 3);

        mockMvc.perform(
                        post("/api/public/goods-survey/responses/{responseId}/application", draft.responseId())
                                .header("X-Survey-Edit-Token", draft.editToken())
                                .header("Idempotency-Key", "idempotency-flea-3")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(applicationBody(photoIds, true, false))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(fulfillmentRepository.count()).isZero();
    }

    @Test
    void 본문을_읽지_못하면_읽지_못했다고_거절한다() throws Exception {
        Draft draft = fleaDirectPurchase();

        mockMvc.perform(
                        post("/api/public/goods-survey/responses/{responseId}/application", draft.responseId())
                                .header("X-Survey-Edit-Token", draft.editToken())
                                .header("Idempotency-Key", "idempotency-flea-4")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"goodsType\": ")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(fulfillmentRepository.count()).isZero();
    }

    /** 플리마켓 랜딩에서 곧장 제작 정보로 온 사람 한 명. */
    private Draft fleaDirectPurchase() throws Exception {
        String createBody = """
                {
                  "questionnaireVersion": "2026-07-25-v2",
                  "selectedGoods": "figure",
                  "tracking": {"visitId": "visit-flea", "device": {"category": "mobile"}},
                  "channel": "flea"
                }
                """;
        String created = mockMvc.perform(
                        post("/api/public/goods-survey/responses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(created).path("data");
        String responseId = data.path("responseId").asText();
        String editToken = data.path("editToken").asText();

        mockMvc.perform(
                        post("/api/public/goods-survey/responses/{responseId}/direct-purchase", responseId)
                                .header("X-Survey-Edit-Token", editToken)
                )
                .andExpect(status().isOk());

        return new Draft(responseId, editToken);
    }

    /**
     * 올라간 것으로 쳐 둔 사진.
     *
     * 이 검사가 보려는 것은 신청서를 읽는 층이라 저장소까지 오가지 않는다.
     * 제출은 확인된 사진이 그 응답에 붙어 있는지만 본다.
     */
    private List<String> confirmedPhotos(String responseId, int count) {
        Instant now = Instant.now();
        return IntStream.range(0, count)
                .mapToObj(index -> {
                    String photoId = "photo-" + index + "-" + responseId.substring(0, 8);
                    GoodsSurveyPhoto photo = GoodsSurveyPhoto.pending(
                            photoId,
                            responseId,
                            "client-file-" + index,
                            "goods/" + responseId + "/" + index + ".jpg",
                            "image/jpeg",
                            1024L,
                            now.plusSeconds(600)
                    );
                    photo.confirm(1024L, now);
                    photoRepository.save(photo);
                    return photoId;
                })
                .collect(Collectors.toList());
    }

    /**
     * 화면이 보내는 신청서.
     *
     * @param withKeyringFlag 낱개 키링 값을 실을지. 화면은 아이마다 키링을 받게
     *                        바뀌면서 이 값을 한동안 보내지 않았다.
     * @param privacyAgreed   개인정보 동의를 실을지. 안 실으면 항목 자체가 빠진다.
     */
    private String applicationBody(
            List<String> photoIds,
            boolean withKeyringFlag,
            boolean privacyAgreed
    ) {
        String quotedPhotoIds = photoIds.stream()
                .map(photoId -> "\"" + photoId + "\"")
                .collect(Collectors.joining(","));
        return """
                {
                  "goodsType": "figure",
                  "customGoods": "",
                  "guardianName": "보호자",
                  "phone": "010-1234-5678",
                  "deliveryMethod": "shipping",
                  "postalCode": "01811",
                  "address": "서울 노원구 공릉로 232",
                  "addressDetail": "",
                  "pets": [
                    {
                      "petName": "몽이",
                      "photoIds": [%s],
                      "publicPhotoIds": [],
                      "keyringAdded": false,
                      "lowPhotoAcknowledged": false
                    }
                  ],
                  %s
                  "conversionEventId": "conversion-flea",
                  "tracking": {"visitId": "visit-flea", "device": {"category": "mobile"}},
                  %s
                  "shippingConfirmed": true,
                  "marketingAgreed": false
                }
                """.formatted(
                quotedPhotoIds,
                withKeyringFlag ? "\"keyringAdded\": false," : "",
                privacyAgreed ? "\"privacyAgreed\": true," : ""
        );
    }

    private record Draft(String responseId, String editToken) {
    }
}
