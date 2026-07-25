package com.pawever.backend.goodssurvey.controller;

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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.cors.allowed-origins=https://pawever-landing.pages.dev,https://feat-goods-survey-landing.pawever-landing.pages.dev"
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
                        now.plusSeconds(3600)
                )
        );
    }

    @Test
    void anonymousBrowserCanCreateAndCompleteAnInternalSurvey() throws Exception {
        String createBody = """
                {
                  "questionnaireVersion": "2026-07-25-v2",
                  "selectedGoods": "acrylic",
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
                  "answers": {"q1": "current_only"},
                  "currentQuestionId": "q1",
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
                .andExpect(jsonPath("$.data.remaining").value(72))
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
