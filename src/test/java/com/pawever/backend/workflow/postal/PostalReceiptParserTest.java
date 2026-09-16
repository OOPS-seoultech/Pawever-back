package com.pawever.backend.workflow.postal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 우체국 내역을 줄 단위로 읽는 규칙.
 *
 * <p>근거: PRD §13 "실제 입력은 13자리 우편물번호 / 요금 / 5자리 우편번호 / 이름
 * 표기와 '통상 반송불요 20g' 보조행이다."
 */
class PostalReceiptParserTest {

    private static final String TWO_ENTRIES =
            """
            1234567890123\t1,800\t01811\t황성욱 보리
            통상 반송불요 20g
            9876543210987\t1,800\t06236\t김민지 초코
            통상 반송불요 20g
            """;

    @Test
    void 한_건은_접수줄과_보조행_두_줄로_읽는다() {
        var result = PostalReceiptParser.parse(TWO_ENTRIES);

        assertThat(result.errors()).isEmpty();
        assertThat(result.receipts()).hasSize(2);
        var first = result.receipts().get(0);
        assertThat(first.trackingNumber()).isEqualTo("1234567890123");
        assertThat(first.postageKrw()).isEqualTo(1_800);
        assertThat(first.postalCode()).isEqualTo("01811");
        assertThat(first.recipientLabel()).isEqualTo("황성욱 보리");
        assertThat(first.service().returnPolicy()).isEqualTo("반송불요");
        assertThat(first.service().weightGrams()).isEqualTo(20);
        assertThat(first.hasIssues()).isFalse();
    }

    @Test
    void 송장번호와_우편번호의_앞자리_0을_지키지_않으면_번호가_사라진다() {
        // 숫자로 바꾸면 앞자리 0이 없어지고, 없어진 번호는 다시 만들 수 없다.
        var result =
                PostalReceiptParser.parse(
                        """
                        0000000000123\t1,800\t01811\t황성욱 보리
                        통상 반송불요 20g
                        """);

        var only = result.receipts().get(0);
        assertThat(only.trackingNumber()).isEqualTo("0000000000123");
        assertThat(only.postalCode()).isEqualTo("01811");
    }

    @Test
    void 읽지_못한_줄은_버리지_않고_오류로_남긴다() {
        // 조용히 버리면 43건을 넣었는데 41건만 처리되고도 아무도 모른다.
        var result =
                PostalReceiptParser.parse(
                        """
                        우편물번호\t요금\t우편번호\t받는분
                        1234567890123\t1,800\t01811\t황성욱 보리
                        통상 반송불요 20g
                        12345\t깨진 줄
                        """);

        assertThat(result.receipts()).hasSize(1);
        assertThat(result.errors())
                .extracting(PostalReceiptParser.LineError::code)
                .containsExactly("UNRECOGNIZED_LINE", "MALFORMED_ENTRY");
    }

    @Test
    void 보조행이_없으면_문제로_표시한다() {
        var result =
                PostalReceiptParser.parse("1234567890123\t1,800\t01811\t황성욱 보리\n");

        assertThat(result.receipts().get(0).issues()).contains("MISSING_SERVICE_LINE");
    }

    @Test
    void 같은_송장번호가_두_번_들어오면_양쪽_모두_표시한다() {
        // 어느 쪽이 맞는지는 원문을 본 사람이 정한다.
        var result =
                PostalReceiptParser.parse(
                        """
                        1234567890123\t1,800\t01811\t황성욱 보리
                        통상 반송불요 20g
                        1234567890123\t1,800\t06236\t김민지 초코
                        통상 반송불요 20g
                        """);

        assertThat(result.receipts())
                .allSatisfy(r -> assertThat(r.issues()).contains("DUPLICATE_TRACKING_IN_INPUT"));
    }

    @Test
    void 이름_표기는_공백만_고르고_글자는_그대로_둔다() {
        // 어디까지가 보호자이고 어디부터가 아이 이름인지 여기서 자르지 않는다.
        var result =
                PostalReceiptParser.parse(
                        """
                        1234567890123\t1,800\t01811\t  황성욱   보리
                        통상 반송불요 20g
                        """);

        assertThat(result.receipts().get(0).recipientLabel()).isEqualTo("황성욱 보리");
    }
}
