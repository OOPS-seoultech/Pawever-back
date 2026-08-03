package com.pawever.backend.goodssurvey.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoodsSurveyCsvTest {

    @Test
    void quotesValuesThatWouldOtherwiseBreakTheColumns() {
        // 주소에는 쉼표가, 사연에는 줄바꿈과 따옴표가 실제로 들어온다.
        assertThat(GoodsSurveyCsv.escape("서울시 노원구, 101동")).isEqualTo("\"서울시 노원구, 101동\"");
        assertThat(GoodsSurveyCsv.escape("첫 줄\n둘째 줄")).isEqualTo("\"첫 줄\n둘째 줄\"");
        assertThat(GoodsSurveyCsv.escape("그날 \"괜찮다\"고 했다"))
                .isEqualTo("\"그날 \"\"괜찮다\"\"고 했다\"");
    }

    @Test
    void leavesPlainValuesAlone() {
        assertThat(GoodsSurveyCsv.escape("몽이")).isEqualTo("몽이");
        assertThat(GoodsSurveyCsv.escape(null)).isEmpty();
    }

    @Test
    void startsWithABomSoExcelReadsKoreanCorrectly() {
        String document = GoodsSurveyCsv.document(
                List.of("이름", "주소"),
                List.of(List.of("몽이", "서울시 노원구, 101동"))
        );

        assertThat(document).startsWith(GoodsSurveyCsv.BOM);
        assertThat(document).contains("이름,주소\n");
        assertThat(document).contains("몽이,\"서울시 노원구, 101동\"\n");
    }
}
