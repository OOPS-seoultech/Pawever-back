package com.pawever.backend.goodssurvey.controller;

import com.pawever.backend.goodssurvey.entity.GoodsSalesChannel;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyCampaign;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyCampaignRepository;
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

    @BeforeEach
    void setUp() {
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
}
