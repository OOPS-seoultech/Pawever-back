package com.pawever.backend.goodssurvey.event;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 신청이 어디서 왔는지 한 줄로 만든다.
 *
 * 화면이 보내는 tracking 은 {visitId, entryPath, firstTouch, lastTouch, ...}
 * 모양이고 utm 값은 그 안에 들어 있다. 알림에 이 덩어리를 그대로 실을 수는
 * 없으므로 사람이 읽을 한 줄로 줄인다.
 *
 * 마지막 접점을 쓴다. 처음 어떻게 알았는지보다, 무엇을 보고 지금 신청까지
 * 왔는지가 광고를 더 태울지 말지를 가른다.
 */
class TrafficSourceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String of(String json) {
        return TrafficSource.describe(MAPPER.readTree(json));
    }

    @Test
    void 마지막_접점의_소스와_매체를_적는다() {
        assertThat(of("""
                {"lastTouch":{"utm_source":"instagram","utm_medium":"cpc"}}
                """)).isEqualTo("instagram / cpc");
    }

    @Test
    void 매체가_없으면_소스만_적는다() {
        assertThat(of("""
                {"lastTouch":{"utm_source":"instagram"}}
                """)).isEqualTo("instagram");
    }

    @Test
    void 마지막_접점이_비었으면_처음_접점을_쓴다() {
        // 광고를 보고 들어왔다가 나중에 직접 들어와 신청하는 흐름이 있다.
        // 그때 마지막만 보면 광고가 한 일이 통째로 사라진다.
        assertThat(of("""
                {"firstTouch":{"utm_source":"threads","utm_medium":"organic"},"lastTouch":{}}
                """)).isEqualTo("threads / organic");
    }

    @Test
    void utm_이_없어도_광고_식별자가_있으면_적는다() {
        assertThat(of("""
                {"lastTouch":{"fbclid":"abc123"}}
                """)).isEqualTo("meta (fbclid)");
        assertThat(of("""
                {"lastTouch":{"gclid":"xyz789"}}
                """)).isEqualTo("google (gclid)");
    }

    @Test
    void 아무것도_없으면_직접_유입이다() {
        assertThat(of("{}")).isEqualTo("직접 유입");
        assertThat(of("""
                {"lastTouch":{},"firstTouch":{}}
                """)).isEqualTo("직접 유입");
    }

    @Test
    void 들어온_경로가_있으면_직접_유입에_덧붙인다() {
        // 어느 화면에서 시작했는지는 남는다. 인스타 프로필 링크와 검색
        // 유입을 구분할 단서가 이것뿐인 경우가 있다.
        assertThat(of("""
                {"entryPath":"/goods"}
                """)).isEqualTo("직접 유입 (/goods)");
    }

    @Test
    void 저장된_값이_없거나_망가져도_터지지_않는다() {
        // 알림 하나 때문에 접수가 실패해서는 안 된다.
        assertThat(TrafficSource.describe(null)).isEqualTo("직접 유입");
        assertThat(of("[]")).isEqualTo("직접 유입");
        assertThat(of("\"instagram\"")).isEqualTo("직접 유입");
    }
}
