package com.pawever.backend.goodssurvey.service;

import com.pawever.backend.global.exception.CustomException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoodsSurveyAnswerValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GoodsSurveyAnswerValidator validator = new GoodsSurveyAnswerValidator(MAPPER);

    private void validate(Map<String, String> answers) {
        Map<String, JsonNode> nodes = new HashMap<>();
        answers.forEach((questionId, value) ->
                nodes.put(questionId, MAPPER.getNodeFactory().textNode(value))
        );
        validator.validateDraft(nodes, 1_000L, Map.of(), MAPPER.getNodeFactory().objectNode());
    }

    @Test
    void acceptsSplitChoicesForQuestionsThatOutgrewFiveOptions() {
        assertThatCode(() -> validate(Map.of("q3", "6"))).doesNotThrowAnyException();
        assertThatCode(() -> validate(Map.of("q12", "6"))).doesNotThrowAnyException();
        assertThatCode(() -> validate(Map.of("q33", "6"))).doesNotThrowAnyException();
        assertThatCode(() -> validate(Map.of("q33", "7"))).doesNotThrowAnyException();
    }

    @Test
    void keepsTheFiveChoiceLimitForEveryOtherNumberedQuestion() {
        assertThatThrownBy(() -> validate(Map.of("q5", "6")))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> validate(Map.of("q13", "6")))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> validate(Map.of("q3", "7")))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> validate(Map.of("q33", "8")))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void acceptsBothHalvesOfTheSplitQ18AndTheValueItReplaced() {
        assertThatCode(() -> validate(Map.of("q18", "late"))).doesNotThrowAnyException();
        assertThatCode(() -> validate(Map.of("q18", "never"))).doesNotThrowAnyException();
        assertThatCode(() -> validate(Map.of("q18", "late_or_never"))).doesNotThrowAnyException();
    }

    @Test
    void allowsTheQ18TailOnlyWhenTheAnswerIsAboutTheFarewellPeriod() {
        assertThatCode(() -> validate(Map.of("q18", "late", "q18_1", "1")))
                .doesNotThrowAnyException();
        assertThatCode(() -> validate(Map.of("q18", "late_or_never", "q18_1", "1")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validate(Map.of("q18", "never", "q18_1", "1")))
                .isInstanceOf(CustomException.class);
    }

    private void validateMulti(Map<String, Object> answers) {
        Map<String, JsonNode> nodes = new HashMap<>();
        answers.forEach((questionId, value) -> {
            if (value instanceof java.util.List<?> list) {
                var array = MAPPER.getNodeFactory().arrayNode();
                list.forEach(item -> array.add(String.valueOf(item)));
                nodes.put(questionId, array);
            } else {
                nodes.put(questionId, MAPPER.getNodeFactory().textNode(String.valueOf(value)));
            }
        });
        validator.validateDraft(nodes, 1_000L, Map.of(), MAPPER.getNodeFactory().objectNode());
    }

    @Test
    void acceptsMultipleChoicesOnTheQuestionsThatBecameMultiSelect() {
        assertThatCode(() -> validateMulti(Map.of("q4", List.of("small_change", "diagnosed"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> validateMulti(Map.of("q8", List.of("anniversary", "medical"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> validateMulti(Map.of("q11", "record", "q11_1a", List.of("1", "2"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> validateMulti(Map.of("q30", List.of("1", "3"))))
                .doesNotThrowAnyException();
    }

    @Test
    void opensTailQuestionsWhenAnyOfTheMultiSelectedValuesMatches() {
        // Q4에서 2번과 3번을 함께 고르면 Q4-1과 Q4-2가 둘 다 열린다.
        assertThatCode(() -> validateMulti(Map.of(
                "q4", List.of("small_change", "diagnosed"),
                "q4_1", "1",
                "q4_2", List.of("1")
        ))).doesNotThrowAnyException();

        // 고르지 않은 값의 꼬리 문항은 여전히 막는다.
        assertThatThrownBy(() -> validateMulti(Map.of(
                "q4", List.of("small_change"),
                "q4_2", List.of("1")
        ))).isInstanceOf(CustomException.class);
    }

    @Test
    void keepsExclusiveChoicesFromBeingCombined() {
        assertThatThrownBy(() -> validateMulti(Map.of("q7", List.of("1", "5"))))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> validateMulti(Map.of("q8", List.of("anniversary", "not_yet"))))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> validateMulti(Map.of("q30", List.of("1", "5"))))
                .isInstanceOf(CustomException.class);

        assertThatCode(() -> validateMulti(Map.of("q8", List.of("not_yet"))))
                .doesNotThrowAnyException();
        // "아직 생각해본 적 없다"를 고르면 Q9·Q10은 물어보지 않는다.
        assertThatThrownBy(() -> validateMulti(Map.of("q8", List.of("not_yet"), "q9", "1")))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void acceptsFreeTextOnlyWhenTheDirectInputChoiceIsSelected() {
        assertThatCode(() -> validateMulti(Map.of("q17", List.of("6"), "q17_text", "장난감")))
                .doesNotThrowAnyException();
        assertThatCode(() -> validateMulti(Map.of(
                "q23", "memory", "q23_1a", "6", "q23_1a_text", "손편지"
        ))).doesNotThrowAnyException();

        // 직접 입력을 고르지 않았으면 자유 텍스트를 받지 않는다.
        assertThatThrownBy(() -> validateMulti(Map.of("q17", List.of("1"), "q17_text", "장난감")))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> validateMulti(Map.of("q17_text", "장난감")))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void rejectsFreeTextThatIsBlankOrTooLong() {
        assertThatThrownBy(() -> validateMulti(Map.of("q17", List.of("6"), "q17_text", "   ")))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> validateMulti(Map.of(
                "q17", List.of("6"), "q17_text", "가".repeat(101)
        ))).isInstanceOf(CustomException.class);
        assertThatCode(() -> validateMulti(Map.of(
                "q17", List.of("6"), "q17_text", "가".repeat(100)
        ))).doesNotThrowAnyException();
    }

    @Test
    void acceptsTheAddedNoneChoiceOnTheUnmetInformationQuestions() {
        assertThatCode(() -> validate(Map.of("q2", "current", "q29_current", "none")))
                .doesNotThrowAnyException();
        assertThatCode(() -> validate(Map.of("q2", "recent_departed", "q29_departed", "none")))
                .doesNotThrowAnyException();
    }
}
