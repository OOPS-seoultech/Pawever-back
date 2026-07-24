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

@Component
public class GoodsSurveyAnswerValidator {

    private static final int MAX_ANSWERS_BYTES = 64 * 1024;
    private static final int MAX_TRACKING_BYTES = 16 * 1024;
    private static final long MAX_SURVEY_ACTIVE_MS = 6 * 60 * 60 * 1000L;
    private static final long MAX_QUESTION_ACTIVE_MS = 4 * 60 * 60 * 1000L;

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
            "q29_1d", "q29_1e", "q30", "q31", "q32", "q33"
    );

    private static final Set<String> TERMINATING_Q1 = Set.of("no_experience", "prefer_not");
    private static final Set<String> VALID_Q1 = Set.of(
            "current_only", "current_and_loss", "loss_only", "no_experience", "prefer_not"
    );
    private static final Set<String> FORBIDDEN_TRACKING_FIELDS = Set.of(
            "answers", "answer", "phone", "address", "addressDetail",
            "guardianName", "petName", "email", "photo", "photos", "file", "files"
    );
    private static final Set<String> NUMBERED_OPTIONS = Set.of("1", "2", "3", "4", "5");
    private static final Set<String> MULTI_QUESTION_IDS = Set.of("q4_2", "q7", "q27");
    private static final Map<String, Integer> MAX_MULTI_SELECTIONS = Map.of(
            "q4_2", 5,
            "q7", 5,
            "q27", 2
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
            Map.entry("q18", Set.of("healthy", "aging", "diagnosis", "care", "late_or_never")),
            Map.entry("q19", Set.of("emotion", "timing", "search", "trust", "other")),
            Map.entry("q21", Set.of("healthy", "aging", "signal", "diagnosis", "later")),
            Map.entry("q23", Set.of("memory", "health", "daily", "info", "never")),
            Map.entry("q29_current", Set.of("health", "quality", "cost", "memory", "emotion")),
            Map.entry("q29_departed", Set.of("health", "quality", "cost", "memory", "emotion"))
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
        if (TERMINATING_Q1.contains(q1) && answers.size() != 1) invalid();
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
        Set<String> allowedOptions = NAMED_OPTIONS.getOrDefault(questionId, NUMBERED_OPTIONS);
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
        requireChild(answers, "q4_1", "q4", Set.of("small_change"));
        requireChild(answers, "q4_2", "q4", Set.of("diagnosed", "continuous_care"));
        requireChild(answers, "q8_1a", "q8", Set.of("anniversary"));
        requireChild(answers, "q8_1b", "q8", Set.of("change"));
        requireChild(answers, "q8_1c", "q8", Set.of("medical"));
        requireChild(answers, "q8_1d", "q8", Set.of("others"));
        if ("not_yet".equals(textValue(answers, "q8"))
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
        requireChild(answers, "q18_1", "q18", Set.of("late_or_never"));

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
            String parentValue = textValue(answers, parent);
            if (parentValue == null || !allowedParentValues.contains(parentValue)) {
                invalid();
            }
        }
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
