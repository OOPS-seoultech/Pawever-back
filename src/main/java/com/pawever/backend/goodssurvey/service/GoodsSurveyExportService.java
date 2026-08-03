package com.pawever.backend.goodssurvey.service;

import com.pawever.backend.goodssurvey.config.GoodsSurveyProperties;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyPhoto;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyPhotoStatus;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyResponse;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyStory;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyFulfillmentRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyPhotoRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyResponseRepository;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyStoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 운영에서 설문 결과를 꺼내기 위한 CSV 생성.
 *
 * 이름·연락처·주소는 DB에 암호화되어 저장되므로 SQL로는 읽을 수 없다.
 * 애플리케이션을 거쳐야만 복호화되기 때문에 이 통로가 필요하다.
 */
@Service
@RequiredArgsConstructor
public class GoodsSurveyExportService {

    // 사연은 화면 순서대로 고정해 둔다. 응답마다 키가 달라지지 않는다.
    private static final List<String> STORY_FIELDS = List.of(
            "status", "age", "condition", "scene", "changedDay", "startedNow",
            "unsaidSearch", "neededHelp", "postponed", "wishKnownEarlier",
            "finalHelp", "oneLine"
    );

    private final GoodsSurveyResponseRepository responseRepository;
    private final GoodsSurveyFulfillmentRepository fulfillmentRepository;
    private final GoodsSurveyStoryRepository storyRepository;
    private final GoodsSurveyPhotoRepository photoRepository;
    private final GoodsSurveyProperties properties;
    private final ObjectMapper objectMapper;

    /** 굿즈 제작·배송에 필요한 정보. 개인정보가 그대로 들어간다. */
    @Transactional(readOnly = true)
    public String applicationsCsv() {
        Map<String, GoodsSurveyResponse> responses = campaignResponses().stream()
                .collect(Collectors.toMap(GoodsSurveyResponse::getId, Function.identity()));
        Map<String, List<GoodsSurveyPhoto>> photos = confirmedPhotosByResponse();

        List<String> header = List.of(
                "응답ID", "신청일시", "굿즈종류", "직접입력굿즈", "반려견이름",
                "보호자이름", "연락처", "우편번호", "주소", "상세주소",
                "사진수", "사진저장키"
        );

        List<List<String>> rows = fulfillmentRepository.findAll().stream()
                .filter(fulfillment -> responses.containsKey(fulfillment.getResponseId()))
                .sorted(Comparator.comparing(GoodsSurveyFulfillment::getId))
                .map(fulfillment -> {
                    List<GoodsSurveyPhoto> attached =
                            photos.getOrDefault(fulfillment.getResponseId(), List.of());
                    return List.of(
                            fulfillment.getResponseId(),
                            text(fulfillment.getCreatedAt()),
                            fulfillment.getGoodsType(),
                            text(fulfillment.getCustomGoods()),
                            fulfillment.getPetName(),
                            fulfillment.getGuardianName(),
                            fulfillment.getPhone(),
                            fulfillment.getPostalCode(),
                            fulfillment.getAddress(),
                            text(fulfillment.getAddressDetail()),
                            String.valueOf(attached.size()),
                            attached.stream()
                                    .map(GoodsSurveyPhoto::getObjectKey)
                                    .collect(Collectors.joining(" | "))
                    );
                })
                .toList();

        return GoodsSurveyCsv.document(header, rows);
    }

    /** 설문 응답. 문항 하나가 한 열이 되도록 펼친다. */
    @Transactional(readOnly = true)
    public String responsesCsv() {
        List<GoodsSurveyResponse> responses = campaignResponses();
        List<String> questionIds = questionColumns(responses);

        List<String> header = new ArrayList<>(List.of(
                "응답ID", "상태", "설문완료일시", "선택굿즈", "설문소요밀리초",
                "유입소스", "유입매체", "캠페인", "소재"
        ));
        header.addAll(questionIds);

        List<List<String>> rows = responses.stream()
                .sorted(Comparator.comparing(GoodsSurveyResponse::getId))
                .map(response -> {
                    JsonNode answers = readJson(response.getAnswersJson());
                    JsonNode touch = readJson(response.getTrackingJson())
                            .path("attribution").path("lastTouch");
                    List<String> values = new ArrayList<>(List.of(
                            response.getId(),
                            response.getStatus().name(),
                            text(response.getCompletedAt()),
                            text(response.getSelectedGoods()),
                            String.valueOf(response.getSurveyActiveMs()),
                            touch.path("utm_source").asString(""),
                            touch.path("utm_medium").asString(""),
                            touch.path("utm_campaign").asString(""),
                            touch.path("utm_content").asString("")
                    ));
                    questionIds.forEach(id -> values.add(answerText(answers.path(id))));
                    return values;
                })
                .toList();

        return GoodsSurveyCsv.document(header, rows);
    }

    /** 사연. 분석 동의는 필수, 공유 동의는 선택이라 함께 싣는다. */
    @Transactional(readOnly = true)
    public String storiesCsv() {
        List<String> header = new ArrayList<>(List.of(
                "응답ID", "분석동의", "공유동의", "동의버전", "동의일시"
        ));
        header.addAll(STORY_FIELDS);

        List<List<String>> rows = storyRepository.findAll().stream()
                .sorted(Comparator.comparing(GoodsSurveyStory::getId))
                .map(story -> {
                    JsonNode fields = readJson(story.getStoryJson());
                    List<String> values = new ArrayList<>(List.of(
                            story.getResponseId(),
                            String.valueOf(story.isAnalysisAgreed()),
                            String.valueOf(story.isPublishAgreed()),
                            text(story.getConsentVersion()),
                            text(story.getConsentedAt())
                    ));
                    STORY_FIELDS.forEach(field -> values.add(fields.path(field).asString("")));
                    return values;
                })
                .toList();

        return GoodsSurveyCsv.document(header, rows);
    }

    private List<GoodsSurveyResponse> campaignResponses() {
        return responseRepository.findAll().stream()
                .filter(response -> properties.getCampaignId().equals(response.getCampaignId()))
                .toList();
    }

    private Map<String, List<GoodsSurveyPhoto>> confirmedPhotosByResponse() {
        return photoRepository.findAll().stream()
                .filter(photo -> photo.getStatus() == GoodsSurveyPhotoStatus.CONFIRMED)
                .collect(Collectors.groupingBy(GoodsSurveyPhoto::getResponseId));
    }

    /**
     * 실제로 답한 문항만 열로 만든다. 분기 때문에 사람마다 답한 문항이 다르다.
     * 엑셀에서 왼쪽부터 순서대로 읽히도록 문항 번호 순으로 세운다.
     */
    private List<String> questionColumns(List<GoodsSurveyResponse> responses) {
        TreeSet<String> ids = new TreeSet<>(questionOrder());
        responses.forEach(response ->
                readJson(response.getAnswersJson()).propertyNames().forEach(ids::add));
        return List.copyOf(ids);
    }

    private Comparator<String> questionOrder() {
        return Comparator
                .comparingInt(GoodsSurveyExportService::leadingNumber)
                .thenComparing(Comparator.naturalOrder());
    }

    private static int leadingNumber(String questionId) {
        int start = 0;
        while (start < questionId.length() && !Character.isDigit(questionId.charAt(start))) {
            start += 1;
        }
        int end = start;
        while (end < questionId.length() && Character.isDigit(questionId.charAt(end))) {
            end += 1;
        }
        return start == end ? Integer.MAX_VALUE : Integer.parseInt(questionId.substring(start, end));
    }

    /** 복수선택은 배열로 저장된다. 한 칸에 담되 값 구분이 보이도록 이어 붙인다. */
    private String answerText(JsonNode answer) {
        if (answer.isMissingNode() || answer.isNull()) {
            return "";
        }
        if (answer.isArray()) {
            List<String> values = new ArrayList<>();
            answer.forEach(item -> values.add(item.asString("")));
            return String.join(" | ", values);
        }
        return answer.asString("");
    }

    private JsonNode readJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(raw);
        } catch (RuntimeException exception) {
            return objectMapper.createObjectNode();
        }
    }

    private String text(Object value) {
        return Objects.toString(value, "");
    }
}
