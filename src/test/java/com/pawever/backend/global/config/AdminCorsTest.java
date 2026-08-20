package com.pawever.backend.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리자 화면은 랜딩 도메인에서 API 도메인을 부른다.
 *
 * 서버 인가 설정이 아무리 맞아도 CORS 가 없으면 브라우저가 요청 자체를 보내지
 * 않는다. 서버 로그에는 아무것도 남지 않고 화면만 조용히 비어 있어서, 권한
 * 문제로 오해하기 쉽다.
 *
 * 여기서는 관리자 경로가 CORS 대상에 들어 있는지와, 로그인 토큰을 실어 보낼 수
 * 있는지를 본다.
 */
class AdminCorsTest {

    private final SecurityConfig config = new SecurityConfig(null, null, new CorsProperties());

    @Test
    void 관리자_경로가_CORS_대상에_들어_있다() {
        CorsConfiguration resolved = resolve("/api/admin/orders");

        assertThat(resolved)
                .as("CORS 설정이 없으면 브라우저가 관리자 요청을 보내지 않는다")
                .isNotNull();
    }

    @Test
    void 관리자_요청은_로그인_토큰을_실어_보낼_수_있다() {
        CorsConfiguration resolved = resolve("/api/admin/orders");

        // 사전 요청 단계에서 걸리면 본 요청은 아예 나가지 않는다.
        assertThat(resolved.checkHeaders(java.util.List.of("Authorization")))
                .as("Authorization 이 허용 헤더에 없으면 로그인한 채로 부를 수 없다")
                .isNotEmpty();
    }

    @Test
    void 담당자_비활성화에_쓰는_DELETE_가_열려_있다() {
        CorsConfiguration resolved = resolve("/api/admin/accounts/1");

        assertThat(resolved.checkHttpMethod(org.springframework.http.HttpMethod.DELETE))
                .as("DELETE 가 없으면 담당자 계정을 화면에서 막을 수 없다")
                .isNotNull();
    }

    /** 굿즈 설문은 로그인 토큰을 쓰지 않는다. 관리자 쪽을 열면서 같이 넓히지 않는다. */
    @Test
    void 공개_설문_경로는_로그인_토큰을_받지_않는다() {
        CorsConfiguration resolved = resolve("/api/public/goods-survey/campaign");

        assertThat(resolved).isNotNull();
        assertThat(resolved.checkHeaders(java.util.List.of("Authorization"))).isNull();
    }

    private CorsConfiguration resolve(String path) {
        UrlBasedCorsConfigurationSource source = config.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", path);
        request.addHeader("Origin", "http://localhost:3000");
        return source.getCorsConfiguration(request);
    }
}
