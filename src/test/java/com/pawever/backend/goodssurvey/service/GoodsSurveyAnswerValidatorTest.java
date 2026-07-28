package com.pawever.backend.goodssurvey.service;

import com.pawever.backend.global.exception.CustomException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
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

    @Test
    void acceptsTheAddedNoneChoiceOnTheUnmetInformationQuestions() {
        assertThatCode(() -> validate(Map.of("q2", "current", "q29_current", "none")))
                .doesNotThrowAnyException();
        assertThatCode(() -> validate(Map.of("q2", "recent_departed", "q29_departed", "none")))
                .doesNotThrowAnyException();
    }
}
