package com.pawever.backend.goodssurvey.service;

import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class GoodsSurveyAnswerValidator {

    private static final int MAX_ANSWERS_BYTES = 64 * 1024;
    private static final int MAX_TRACKING_BYTES = 16 * 1024;
    private static final long MAX_SURVEY_ACTIVE_MS = 6 * 60 * 60 * 1000L;
    private static final long MAX_QUESTION_ACTIVE_MS = 4 * 60 * 60 * 1000L;

    // 프런트 게이트(goodsSurveySchema.ts의 MIN_ANSWERED_FOR_RESERVATION)를 우회해
    // 자격 문항만 답하고 완료를 호출하는 어뷰징이 무료 제작 슬롯을 선점하지 못하도록,
    // 종료 대상이 아닌 완료 응답은 최소 응답 수를 충족해야 한다. 프런트와 같은 값(5)을 유지한다.
    private static final int MIN_ANSWERS_FOR_RESERVATION = 5;

    private static final Set<String> QUESTION_IDS = Set.of(
            "q1", "q2", "q3", "q4", "q4_1", "q4_2", "q5", "q6", "q7",
            "q8", "q8_1a", "q8_1b", "q8_1c", "q8_1d", "q9", "q10",
            "q11", "q11_1a", "q11_1b", "q11_2b", "q11_1c", "q11_1d", "q11_1e",
            "q12", "q13", "q14", "q14_1h", "q14_1f", "q15",
            "q16", "q16_1a", "q16_1b", "q16_1c", "q16_1d",
            "q17", "q18", "q18_1", "q19", "q19_1a", "q19_1b", "q19_1c",
            "q19_1d", "q19_1e", "q20", "q21", "q21_1",
            "q22_1", "q22_2", "q22_3", "q22_4", "q22_5",
            "q23", "q23_1a", "q23_1b", "q23_1c", "q23_1d",
            "q24", "q25", "q26", "q27",
            "q28_1", "q28_2", "q28_3", "q28_4", "q28_5",
            "q29_current", "q29_departed", "q29_1a", "q29_1b", "q29_1c",
            "q29_1d", "q29_1e", "q30", "q31", "q32", "q33",
            // "직접 입력" 선택지의 자유 텍스트. 프런트 freeTextKey()와 이름이 같다.
            "q17_text", "q23_1a_text", "q23_1b_text", "q23_1c_text", "q23_1d_text"
    );

    private static final int FREE_TEXT_MAX_LENGTH = 100;

    /** 자유 텍스트 키 -> 그 값을 열어주는 문항. 부모에서 "직접 입력"(6번)을 골라야 받는다. */
    private static final Map<String, String> FREE_TEXT_PARENTS = Map.of(
            "q17_text", "q17",
            "q23_1a_text", "q23_1a",
            "q23_1b_text", "q23_1b",
            "q23_1c_text", "q23_1c",
            "q23_1d_text", "q23_1d"
    );
    private static final String FREE_TEXT_OPTION = "6";

    private static final Set<String> TERMINATING_Q1 = Set.of("no_experience", "prefer_not");
    private static final Set<String> VALID_Q1 = Set.of(
            "current_only", "current_and_loss", "loss_only", "no_experience", "prefer_not"
    );
    private static final Set<String> FORBIDDEN_TRACKING_FIELDS = Set.of(
            "answers", "answer", "phone", "address", "addressDetail",
            "guardianName", "petName", "email", "photo", "photos", "file", "files"
    );
    private static final Set<String> NUMBERED_OPTIONS = numberedOptions(5);

    // 노션 개정본에서 하나였던 선택지가 쪼개지면서 5개를 넘긴 문항들.
    // 프런트 goodsSurveySchema.ts의 문항별 선택지 수와 같은 값을 유지해야 하며,
    // 여기에 없는 번호형 문항은 그대로 5개까지만 받는다.
    private static final Map<String, Set<String>> NUMBERED_OPTION_OVERRIDES = Map.of(
            "q3", numberedOptions(6),
            "q12", numberedOptions(6),
            "q17", numberedOptions(6),
            "q23_1a", numberedOptions(6),
            "q23_1b", numberedOptions(6),
            "q23_1c", numberedOptions(6),
            "q23_1d", numberedOptions(6),
            "q33", numberedOptions(7)
    );
    private static final Set<String> MULTI_QUESTION_IDS = Set.of(
            "q4", "q4_2", "q7", "q8", "q11_1a", "q17", "q27", "q30"
    );
    private static final Map<String, Integer> MAX_MULTI_SELECTIONS = Map.of(
            "q4", 5,
            "q4_2", 5,
            "q7", 5,
            "q8", 5,
            "q11_1a", 5,
            "q17", 6,
            "q27", 2,
            "q30", 5
    );

    // 고르면 다른 선택지와 함께 둘 수 없는 값. 프런트 exclusiveOptionIds와 짝이다.
    private static final Map<String, String> EXCLUSIVE_OPTIONS = Map.of(
            "q7", "5",
            "q8", "not_yet",
            "q17", "5",
            "q30", "5"
    );
    private static final Map<String, Set<String>> NAMED_OPTIONS = Map.ofEntries(
            Map.entry("q1", VALID_Q1),
            Map.entry("q2", Set.of("current", "recent_departed", "longest")),
            Map.entry("q4", Set.of("healthy", "small_change", "diagnosed", "continuous_care", "sudden")),
            Map.entry("q8", Set.of("anniversary", "change", "medical", "others", "not_yet")),
            Map.entry("q11", Set.of("record", "search", "consult", "prepare", "none")),
            Map.entry("q11_1b", Set.of("symptom", "treatment", "care", "cost", "farewell")),
            Map.entry("q14", Set.of("health_only", "future_only", "health_first", "future_first", "none")),
            Map.entry("q16", Set.of("medical", "finance", "care", "farewell", "none")),
            // late_or_never는 Q18이 late(이별 무렵)·never(아직 안 찾아봄)로 쪼개지기 전의 값이다.
            // 저장된 임시 응답이 남아 있을 수 있어 계속 받아준다.
            Map.entry("q18", Set.of("healthy", "aging", "diagnosis", "care", "late", "never", "late_or_never")),
            Map.entry("q19", Set.of("emotion", "timing", "search", "trust", "other")),
            Map.entry("q21", Set.of("healthy", "aging", "signal", "diagnosis", "later")),
            Map.entry("q23", Set.of("memory", "health", "daily", "info", "never")),
            Map.entry("q29_current", Set.of("health", "quality", "cost", "memory", "emotion", "none")),
            Map.entry("q29_departed", Set.of("health", "quality", "cost", "memory", "emotion", "none"))
    );

    private final ObjectMapper objectMapper;

    public GoodsSurveyAnswerValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validateDraft(
            Map<String, JsonNode> answers,
            Long surveyActiveMs,
            Map<String, Long> questionActiveMs,
            JsonNode tracking
    ) {
        if (answers == null || answers.size() > QUESTION_IDS.size()) invalid();
        answers.forEach(this::validateAnswer);

        if (surveyActiveMs == null || surveyActiveMs < 0 || surveyActiveMs > MAX_SURVEY_ACTIVE_MS) {
            invalid();
        }
        if (questionActiveMs == null || questionActiveMs.size() > QUESTION_IDS.size()) invalid();
        questionActiveMs.forEach((questionId, activeMs) -> {
            if (!QUESTION_IDS.contains(questionId)
                    || activeMs == null
                    || activeMs < 0
                    || activeMs > MAX_QUESTION_ACTIVE_MS) {
                invalid();
            }
        });

        validateTracking(tracking);
        validateSerializedSize(answers, MAX_ANSWERS_BYTES);
        validateBranchConsistency(answers);
    }

    public void validateComplete(
            Map<String, JsonNode> answers,
            Long surveyActiveMs,
            Map<String, Long> questionActiveMs,
            JsonNode tracking
    ) {
        validateDraft(answers, surveyActiveMs, questionActiveMs, tracking);
        String q1 = textValue(answers, "q1");
        if (q1 == null || !VALID_Q1.contains(q1)) invalid();
        if (TERMINATING_Q1.contains(q1)) {
            if (answers.size() != 1) invalid();
            return;
        }
        if (answers.size() < MIN_ANSWERS_FOR_RESERVATION) {
            throw new CustomException(ErrorCode.SURVEY_INSUFFICIENT_ANSWERS);
        }
    }

    public boolean isTerminated(Map<String, JsonNode> answers) {
        return TERMINATING_Q1.contains(textValue(answers, "q1"));
    }

    public void validateTrackingOnly(JsonNode tracking) {
        validateTracking(tracking);
    }

    public void validateCurrentQuestionId(String currentQuestionId) {
        if (currentQuestionId != null && !QUESTION_IDS.contains(currentQuestionId)) {
            invalid();
        }
    }

    private void validateAnswer(String questionId, JsonNode answer) {
        if (!QUESTION_IDS.contains(questionId) || answer == null) invalid();

        if (FREE_TEXT_PARENTS.containsKey(questionId)) {
            if (!answer.isTextual()) invalid();
            String text = answer.stringValue().trim();
            if (text.isEmpty() || text.length() > FREE_TEXT_MAX_LENGTH) invalid();
            return;
        }

        Set<String> allowedOptions = allowedOptions(questionId);
        if (answer.isTextual()) {
            if (MULTI_QUESTION_IDS.contains(questionId)
                    || !allowedOptions.contains(answer.stringValue())) {
                invalid();
            }
            return;
        }
        if (!MULTI_QUESTION_IDS.contains(questionId)
                || !answer.isArray()
                || answer.size() == 0
                || answer.size() > MAX_MULTI_SELECTIONS.get(questionId)) {
            invalid();
        }
        Set<String> seen = new HashSet<>();
        for (JsonNode option : answer) {
            if (!option.isTextual()
                    || !allowedOptions.contains(option.stringValue())
                    || !seen.add(option.stringValue())) {
                invalid();
            }
        }

        String exclusive = EXCLUSIVE_OPTIONS.get(questionId);
        if (exclusive != null && seen.contains(exclusive) && seen.size() > 1) {
            invalid();
        }
    }

    private void validateTracking(JsonNode tracking) {
        if (tracking == null || !tracking.isObject()) invalid();
        validateSerializedSize(tracking, MAX_TRACKING_BYTES);
        rejectPrivateTrackingFields(tracking);
    }

    private void rejectPrivateTrackingFields(JsonNode node) {
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                if (FORBIDDEN_TRACKING_FIELDS.contains(entry.getKey())) invalid();
                rejectPrivateTrackingFields(entry.getValue());
            });
        } else if (node.isArray()) {
            node.forEach(this::rejectPrivateTrackingFields);
        }
    }

    private void validateBranchConsistency(Map<String, JsonNode> answers) {
        // 자유 텍스트는 부모 문항에서 "직접 입력"을 고른 경우에만 받는다.
        FREE_TEXT_PARENTS.forEach((textKey, parent) -> {
            if (answers.containsKey(textKey)
                    && !selectedValues(answers, parent).contains(FREE_TEXT_OPTION)) {
                invalid();
            }
        });

        requireChild(answers, "q4_1", "q4", Set.of("small_change"));
        requireChild(answers, "q4_2", "q4", Set.of("diagnosed", "continuous_care"));
        requireChild(answers, "q8_1a", "q8", Set.of("anniversary"));
        requireChild(answers, "q8_1b", "q8", Set.of("change"));
        requireChild(answers, "q8_1c", "q8", Set.of("medical"));
        requireChild(answers, "q8_1d", "q8", Set.of("others"));
        if (selectedValues(answers, "q8").contains("not_yet")
                && (answers.containsKey("q9") || answers.containsKey("q10"))) {
            invalid();
        }

        requireChild(answers, "q11_1a", "q11", Set.of("record"));
        requireChild(answers, "q11_1b", "q11", Set.of("search"));
        requireChild(answers, "q11_2b", "q11_1b", Set.of("farewell"));
        requireChild(answers, "q11_1c", "q11", Set.of("consult"));
        requireChild(answers, "q11_1d", "q11", Set.of("prepare"));
        requireChild(answers, "q11_1e", "q11", Set.of("none"));

        requireChild(answers, "q14_1h", "q14", Set.of("health_only", "health_first"));
        requireChild(answers, "q14_1f", "q14", Set.of("future_only", "future_first"));
        if ("none".equals(textValue(answers, "q14")) && answers.containsKey("q15")) invalid();

        requireChild(answers, "q16_1a", "q16", Set.of("medical"));
        requireChild(answers, "q16_1b", "q16", Set.of("finance"));
        requireChild(answers, "q16_1c", "q16", Set.of("care"));
        requireChild(answers, "q16_1d", "q16", Set.of("farewell"));
        requireChild(answers, "q18_1", "q18", Set.of("late", "late_or_never"));

        requireChild(answers, "q19_1a", "q19", Set.of("emotion"));
        requireChild(answers, "q19_1b", "q19", Set.of("timing"));
        requireChild(answers, "q19_1c", "q19", Set.of("search"));
        requireChild(answers, "q19_1d", "q19", Set.of("trust"));
        requireChild(answers, "q19_1e", "q19", Set.of("other"));

        requireChild(answers, "q21_1", "q21", Set.of("later"));
        requireChild(answers, "q23_1a", "q23", Set.of("memory"));
        requireChild(answers, "q23_1b", "q23", Set.of("health"));
        requireChild(answers, "q23_1c", "q23", Set.of("daily"));
        requireChild(answers, "q23_1d", "q23", Set.of("info"));

        if (answers.containsKey("q29_current")
                && !"current".equals(textValue(answers, "q2"))) {
            invalid();
        }
        if (answers.containsKey("q29_departed")
                && !"recent_departed".equals(textValue(answers, "q2"))
                && !"longest".equals(textValue(answers, "q2"))) {
            invalid();
        }

        String q29 = answers.containsKey("q29_current")
                ? textValue(answers, "q29_current")
                : textValue(answers, "q29_departed");
        requireChildValue(answers, "q29_1a", q29, Set.of("health"));
        requireChildValue(answers, "q29_1b", q29, Set.of("quality"));
        requireChildValue(answers, "q29_1c", q29, Set.of("cost"));
        requireChildValue(answers, "q29_1d", q29, Set.of("memory"));
        requireChildValue(answers, "q29_1e", q29, Set.of("emotion"));
    }

    private void requireChild(
            Map<String, JsonNode> answers,
            String child,
            String parent,
            Set<String> allowedParentValues
    ) {
        if (answers.containsKey(child)) {
            // 부모가 복수선택이면 고른 값 중 하나만 해당해도 꼬리 문항이 열린다.
            Set<String> parentValues = selectedValues(answers, parent);
            if (parentValues.stream().noneMatch(allowedParentValues::contains)) {
                invalid();
            }
        }
    }

    /** 단일·복수선택을 가리지 않고 고른 값들을 돌려준다. */
    private Set<String> selectedValues(Map<String, JsonNode> answers, String questionId) {
        JsonNode value = answers.get(questionId);
        if (value == null) return Set.of();
        if (value.isTextual()) return Set.of(value.stringValue());
        if (!value.isArray()) return Set.of();

        Set<String> values = new HashSet<>();
        value.forEach(option -> {
            if (option.isTextual()) values.add(option.stringValue());
        });
        return values;
    }

    private void requireChildValue(
            Map<String, JsonNode> answers,
            String child,
            String parentValue,
            Set<String> allowedParentValues
    ) {
        if (answers.containsKey(child)) {
            if (parentValue == null || !allowedParentValues.contains(parentValue)) {
                invalid();
            }
        }
    }

    private static Set<String> numberedOptions(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(String::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> allowedOptions(String questionId) {
        Set<String> named = NAMED_OPTIONS.get(questionId);
        if (named != null) {
            return named;
        }
        return NUMBERED_OPTION_OVERRIDES.getOrDefault(questionId, NUMBERED_OPTIONS);
    }

    private String textValue(Map<String, JsonNode> answers, String questionId) {
        JsonNode value = answers.get(questionId);
        return value != null && value.isTextual() ? value.stringValue() : null;
    }

    private void validateSerializedSize(Object value, int maximumBytes) {
        try {
            int bytes = objectMapper.writeValueAsString(value)
                    .getBytes(StandardCharsets.UTF_8)
                    .length;
            if (bytes > maximumBytes) invalid();
        } catch (JacksonException exception) {
            invalid();
        }
    }

    private void invalid() {
        throw new CustomException(ErrorCode.SURVEY_INVALID_ANSWERS);
    }
}
