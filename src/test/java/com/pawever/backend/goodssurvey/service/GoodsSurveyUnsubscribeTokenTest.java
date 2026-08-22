package com.pawever.backend.goodssurvey.service;

import com.pawever.backend.global.security.HmacHasher;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 수신거부 링크에 실리는 값.
 *
 * 메일에 주소를 그대로 실으면 링크를 거치는 모든 곳에 그 주소가 남고, 남의
 * 주소를 적어 넣어 대신 해지시킬 수 있다. 그래서 서명한 값을 싣는다.
 */
class GoodsSurveyUnsubscribeTokenTest {

    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final String OTHER_KEY = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=";

    private final GoodsSurveyUnsubscribeToken token =
            new GoodsSurveyUnsubscribeToken(new HmacHasher(KEY));

    @Test
    void 만든_값은_다시_읽힌다() {
        assertThat(token.verify(token.issue(42L))).contains(42L);
    }

    @Test
    void 값_안에_이메일이_들어_있지_않다() {
        // 열어 봐도 번호밖에 없어야 한다. 링크는 메일 본문과 방문 기록에 남는다.
        String issued = token.issue(42L);
        String decoded = new String(Base64.getUrlDecoder().decode(issued));

        assertThat(decoded).doesNotContain("@");
        assertThat(decoded).startsWith("42:");
    }

    @Test
    void 서명이_없으면_받지_않는다() {
        // 번호만 적어 보내는 경우다. 이게 통하면 1번부터 올려 가며 남을 해지시킬 수 있다.
        String forged = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("42:".getBytes());

        assertThat(token.verify(forged)).isEmpty();
    }

    @Test
    void 다른_번호의_서명을_가져다_쓰지_못한다() {
        String issued = token.issue(42L);
        String decoded = new String(Base64.getUrlDecoder().decode(issued));
        String signature = decoded.substring(decoded.indexOf(':') + 1);

        String swapped = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("43:" + signature).getBytes());

        assertThat(token.verify(swapped)).isEmpty();
    }

    @Test
    void 키가_다르면_받지_않는다() {
        GoodsSurveyUnsubscribeToken other =
                new GoodsSurveyUnsubscribeToken(new HmacHasher(OTHER_KEY));

        assertThat(other.verify(token.issue(42L))).isEmpty();
    }

    @Test
    void 이상한_값이_와도_터지지_않는다() {
        // 링크가 잘려 오거나 사람이 주소창을 고친 경우다. 여기서 예외가 나가면
        // 화면에 500 이 뜨고, 그것만으로 값을 넣어 볼 만한 자리라는 것을 알린다.
        for (String broken : new String[]{null, "", "   ", "!!!", "not-base64", "===",
                Base64.getUrlEncoder().withoutPadding().encodeToString("숫자아님:서명".getBytes()),
                Base64.getUrlEncoder().withoutPadding().encodeToString("서명없음".getBytes())}) {
            Optional<Long> result = token.verify(broken);
            assertThat(result).as("입력: %s", broken).isEmpty();
        }
    }
}
