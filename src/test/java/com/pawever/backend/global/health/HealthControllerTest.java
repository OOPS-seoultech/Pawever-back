package com.pawever.backend.global.health;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * /health 가 무엇을 돌려주는지.
 *
 * 배포 스크립트가 이 응답에서 커밋을 뽑아, 자기가 방금 빌드한 것과 같은지
 * 확인한 뒤에만 트래픽을 넘긴다. 그래서 이 모양은 우리끼리의 약속이 아니라
 * 배포가 의지하는 계약이다.
 *
 * 확인하는 방법도 똑같이 적어 둔다 —
 *   curl .../health | grep -o '"commit":"[^"]*"' | cut -d'"' -f4
 */
class HealthControllerTest {

    @Test
    void 살아_있음과_함께_커밋을_알린다() {
        Map<String, String> body = new HealthController("1a2b3c4").health().getBody();

        assertThat(body).containsEntry("status", "ok");
        assertThat(body).containsEntry("commit", "1a2b3c4");
    }

    @Test
    void 커밋을_모르면_unknown_이라고_말한다() {
        // 로컬에서 그냥 띄우면 빌드 인자가 없다. 빈 문자열을 그대로 내보내면
        // 배포 쪽 비교가 "빈 값 == 빈 값"으로 통과해 버린다.
        assertThat(new HealthController("").health().getBody())
                .containsEntry("commit", "unknown");
        assertThat(new HealthController(null).health().getBody())
                .containsEntry("commit", "unknown");
    }

    @Test
    void 커밋에는_따옴표를_깨뜨릴_것이_들어오지_않는다() {
        // 배포는 이 응답을 JSON 파서가 아니라 grep 으로 읽는다. 커밋 해시는
        // 16진수라 문제될 것이 없지만, 값이 바뀌면 그 전제도 같이 깨진다.
        assertThat(new HealthController("1a2b3c4").health().getBody().get("commit"))
                .matches("[0-9a-fA-F]+");
    }
}
