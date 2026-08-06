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

import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 운영에서 설문 결과를 꺼내기 위한 CSV 생성.
 *
 * 이름·연락처·주소는 DB에 암호화되어 저장되므로 SQL로는 읽을 수 없다.
 * 애플리케이션을 거쳐야만 복호화되기 때문에 이 통로가 필요하다.
 */
@Service
@RequiredArgsConstructor
public class GoodsSurveyExportService {

    /**
     * 화면에서 쓰는 굿즈 이름.
     *
     * 코드값만 보면 무엇을 만들어야 하는지 알 수 없어서 제작·배송 목록에 함께 싣는다.
     * 굿즈가 늘면 여기에도 더해야 한다. 모르는 값은 코드값을 그대로 둬서 빈칸이 되지 않게 한다.
     */
    private static final Map<String, String> GOODS_NAMES = Map.of(
            "acrylic", "아크릴 얼굴 키링",
            "face", "3D 얼굴 키링",
            "backplate", "뒷판형 3D 얼굴 키링",
            "figure", "3D 전신 피규어",
            "custom", "원하는 형태 직접 제안"
    );

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
    private final GoodsSurveyPhotoStorage photoStorage;
    private final GoodsSurveyProperties properties;
    private final ObjectMapper objectMapper;

    /** 굿즈 제작·배송에 필요한 정보. 개인정보가 그대로 들어간다. */
    @Transactional(readOnly = true)
    public String applicationsCsv() {
        Map<String, GoodsSurveyResponse> responses = campaignResponses().stream()
                .collect(Collectors.toMap(GoodsSurveyResponse::getId, Function.identity()));
        Map<String, List<GoodsSurveyPhoto>> photos = confirmedPhotosByResponse();

        List<String> header = List.of(
                "응답ID", "신청일시", "굿즈이름", "굿즈종류", "직접입력굿즈", "반려견이름",
                "보호자이름", "연락처", "우편번호", "주소", "상세주소",
                "사진수", "사진저장키",
                // 동의 없이는 신청이 성립하지 않지만, 그것만으로는 기록이 아니다.
                // 언제 어떤 문구에 동의했는지 남아야 나중에 보여줄 수 있다.
                "개인정보동의버전", "개인정보동의일시"
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
                            goodsName(fulfillment.getGoodsType()),
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
                                    .collect(Collectors.joining(" | ")),
                            text(fulfillment.getPrivacyConsentVersion()),
                            text(fulfillment.getPrivacyConsentedAt())
                    );
                })
                .toList();

        return GoodsSurveyCsv.document(header, rows);
    }

    /**
     * 제작에 넘길 목록. 개인정보를 뺀다.
     *
     * 만드는 데 필요한 것은 사진과 굿즈 종류, 반려견 이름뿐이다.
     * 보호자 이름·연락처·주소는 배송 단계에서 쓰이므로 여기 담지 않는다.
     */
    @Transactional(readOnly = true)
    public String productionCsv(int from, int to) {
        List<String> header = List.of(
                "번호", "굿즈이름", "굿즈종류", "반려견이름", "사진파일명", "요청사항", "응답ID"
        );

        List<List<String>> rows = productionItems(from, to).stream()
                .map(item -> List.of(
                        String.valueOf(item.order()),
                        goodsName(item.goodsType()),
                        item.goodsType(),
                        item.petName(),
                        item.fileNames().stream().collect(Collectors.joining(" | ")),
                        item.request(),
                        item.responseId()
                ))
                .toList();

        return GoodsSurveyCsv.document(header, rows);
    }

    /**
     * 제작용 사진 묶음.
     *
     * 링크로 넘기면 받는 쪽이 파일을 하나씩 눌러 받아야 하고, 저장된 이름이
     * 무작위라 누구 것인지 알 수 없다. 파일을 통째로 넘기면 그 두 가지가 없다.
     * 사진이 커서 통째로 메모리에 담지 않고 하나씩 흘려보낸다.
     */
    @Transactional(readOnly = true)
    public void writePhotoArchive(int from, int to, OutputStream target) {
        try (ZipOutputStream archive = new ZipOutputStream(target, StandardCharsets.UTF_8)) {
            for (ProductionItem item : productionItems(from, to)) {
                for (int index = 0; index < item.photos().size(); index += 1) {
                    archive.putNextEntry(new ZipEntry(item.fileNames().get(index)));
                    archive.write(photoStorage.download(item.photos().get(index).getObjectKey()));
                    archive.closeEntry();
                }
            }
        } catch (IOException exception) {
            throw new CustomException(ErrorCode.SURVEY_PHOTO_NOT_READY);
        }
    }

    /** 신청이 들어온 순서대로 번호를 매기고, 요청한 구간만 잘라 낸다. */
    private List<ProductionItem> productionItems(int from, int to) {
        Map<String, List<GoodsSurveyPhoto>> photos = confirmedPhotosByResponse();
        Map<String, GoodsSurveyResponse> responses = campaignResponses().stream()
                .collect(Collectors.toMap(GoodsSurveyResponse::getId, Function.identity()));

        List<GoodsSurveyFulfillment> ordered = fulfillmentRepository.findAll().stream()
                .filter(fulfillment -> responses.containsKey(fulfillment.getResponseId()))
                .sorted(Comparator.comparing(GoodsSurveyFulfillment::getCreatedAt)
                        .thenComparing(GoodsSurveyFulfillment::getId))
                .toList();

        List<ProductionItem> items = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index += 1) {
            int order = index + 1;
            if (order < from || order > to) {
                continue;
            }
            GoodsSurveyFulfillment fulfillment = ordered.get(index);
            List<GoodsSurveyPhoto> attached =
                    photos.getOrDefault(fulfillment.getResponseId(), List.of());
            items.add(new ProductionItem(
                    order,
                    fulfillment.getResponseId(),
                    fulfillment.getGoodsType(),
                    fulfillment.getPetName(),
                    text(fulfillment.getCustomGoods()),
                    attached,
                    photoNames(order, fulfillment.getGoodsType(), fulfillment.getPetName(), attached)
            ));
        }
        return items;
    }

    /** 압축을 풀자마자 누구 것인지 보이도록 이름을 붙인다. */
    private List<String> photoNames(
            int order,
            String goodsType,
            String petName,
            List<GoodsSurveyPhoto> photos
    ) {
        List<String> names = new ArrayList<>();
        for (int index = 0; index < photos.size(); index += 1) {
            names.add("%02d_%s_%s_%d.%s".formatted(
                    order,
                    safeFileName(goodsType),
                    safeFileName(petName),
                    index + 1,
                    extensionOf(photos.get(index).getContentType())
            ));
        }
        return names;
    }

    /** 반려견 이름에 파일명으로 쓸 수 없는 글자가 들어올 수 있다. */
    private String safeFileName(String value) {
        String trimmed = Objects.toString(value, "").trim();
        String cleaned = trimmed.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        return cleaned.isEmpty() ? "이름없음" : cleaned;
    }

    /**
     * 광고 소재.
     *
     * 인스타 광고는 utm_content에, 쓰레드 링크는 utm_term에 소재를 담아 왔다.
     * 한쪽만 읽으면 49건이 빈칸이 되므로 있는 쪽을 쓴다.
     */
    private String creative(JsonNode touch) {
        String content = touch.path("utm_content").asString("");
        return content.isBlank() ? touch.path("utm_term").asString("") : content;
    }

    private String clickId(JsonNode touch) {
        String facebook = touch.path("fbclid").asString("");
        return facebook.isBlank() ? touch.path("gclid").asString("") : facebook;
    }

    private String goodsName(String goodsType) {
        return GOODS_NAMES.getOrDefault(goodsType, Objects.toString(goodsType, ""));
    }

    private String extensionOf(String contentType) {
        return switch (Objects.toString(contentType, "")) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }

    private record ProductionItem(
            int order,
            String responseId,
            String goodsType,
            String petName,
            String request,
            List<GoodsSurveyPhoto> photos,
            List<String> fileNames
    ) {
    }

    /** 설문 응답. 문항 하나가 한 열이 되도록 펼친다. */
    @Transactional(readOnly = true)
    public String responsesCsv() {
        List<GoodsSurveyResponse> responses = campaignResponses();
        List<String> questionIds = questionColumns(responses);

        Map<String, GoodsSurveyFulfillment> applied = fulfillmentRepository.findAll().stream()
                .collect(Collectors.toMap(
                        GoodsSurveyFulfillment::getResponseId, Function.identity(), (a, b) -> a));

        List<String> header = new ArrayList<>(List.of(
                "응답ID", "상태",
                "유입소스", "유입매체", "캠페인", "소재",
                "첫유입소스", "첫유입매체", "첫유입캠페인", "첫유입소재",
                "광고클릭ID", "기기",
                "랜딩진입시각", "설문시작시각", "설문완료일시", "설문소요밀리초",
                "마지막문항", "문항별시간JSON",
                "선택굿즈", "신청완료", "최종굿즈", "신청시각"
        ));
        header.addAll(questionIds);

        List<List<String>> rows = responses.stream()
                .sorted(Comparator.comparing(GoodsSurveyResponse::getId))
                .map(response -> {
                    JsonNode answers = readJson(response.getAnswersJson());
                    JsonNode attribution = readJson(response.getTrackingJson()).path("attribution");
                    JsonNode last = attribution.path("lastTouch");
                    JsonNode first = attribution.path("firstTouch");
                    JsonNode device = readJson(response.getTrackingJson()).path("device");
                    GoodsSurveyFulfillment fulfillment = applied.get(response.getId());

                    List<String> values = new ArrayList<>(List.of(
                            response.getId(),
                            response.getStatus().name(),
                            last.path("utm_source").asString(""),
                            last.path("utm_medium").asString(""),
                            last.path("utm_campaign").asString(""),
                            creative(last),
                            first.path("utm_source").asString(""),
                            first.path("utm_medium").asString(""),
                            first.path("utm_campaign").asString(""),
                            creative(first),
                            clickId(last),
                            device.path("category").asString(""),
                            attribution.path("startedAt").asString(""),
                            text(response.getCreatedAt()),
                            text(response.getCompletedAt()),
                            String.valueOf(response.getSurveyActiveMs()),
                            text(response.getCurrentQuestionId()),
                            Objects.toString(response.getQuestionTimingsJson(), "{}"),
                            text(response.getSelectedGoods()),
                            fulfillment == null ? "N" : "Y",
                            fulfillment == null ? "" : fulfillment.getGoodsType(),
                            fulfillment == null ? "" : text(fulfillment.getCreatedAt())
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
