package com.pawever.backend.admin.security;

import com.pawever.backend.admin.config.AdminProperties;
import com.pawever.backend.admin.entity.AdminRole;
import com.pawever.backend.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리자 토큰이 앱 회원 토큰과 섞이지 않는지 본다.
 *
 * 이 계정 하나가 고객 주소와 연락처 전부를 연다. 앱 토큰으로 관리자 요청이
 * 통과되면 소셜 로그인을 한 아무나 그 자리에 설 수 있다.
 */
class AdminTokenProviderTest {

    private static final String ADMIN_SECRET =
            "admin-secret-key-must-be-at-least-256-bits-long-for-hs256-ok";
    private static final String APP_SECRET =
            "pawever-jwt-secret-key-must-be-at-least-256-bits-long-for-hs256";

    private AdminTokenProvider provider(String secret, Instant now) {
        AdminProperties properties = new AdminProperties();
        properties.setJwtSecret(secret);
        properties.setJwtExpirationMillis(8 * 60 * 60 * 1000L);
        return new AdminTokenProvider(properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void 발급한_토큰에서_계정과_역할을_되읽는다() {
        AdminTokenProvider provider = provider(ADMIN_SECRET, Instant.parse("2026-08-20T00:00:00Z"));

        String token = provider.createToken(7L, AdminRole.PRODUCTION);
        AdminPrincipal principal = provider.parse(token);

        assertThat(principal).isNotNull();
        assertThat(principal.accountId()).isEqualTo(7L);
        assertThat(principal.role()).isEqualTo(AdminRole.PRODUCTION);
    }

    @Test
    void 앱_회원_토큰은_관리자로_통과하지_못한다() {
        // 서명 키가 달라 claim 을 보기도 전에 걸린다. 검사를 하나 빠뜨려도
        // 앱 토큰이 관리자 자리에 설 수 없다는 뜻이다.
        JwtTokenProvider appTokens = new JwtTokenProvider(APP_SECRET, 86_400_000L);
        String appToken = appTokens.createToken(1L);

        AdminTokenProvider provider = provider(ADMIN_SECRET, Instant.parse("2026-08-20T00:00:00Z"));

        assertThat(provider.parse(appToken)).isNull();
    }

    @Test
    void 만료된_토큰은_통과하지_못한다() {
        Instant issued = Instant.parse("2026-08-20T00:00:00Z");
        String token = provider(ADMIN_SECRET, issued).createToken(1L, AdminRole.ADMIN);

        // 유효 시간 8시간을 넘긴 시점에서 읽는다.
        AdminTokenProvider later = provider(ADMIN_SECRET, issued.plusSeconds(9 * 3600));

        assertThat(later.parse(token)).isNull();
    }

    @Test
    void 서명_키가_없으면_관리자_통로가_닫힌다() {
        // 기본값을 두지 않는다. 두면 그 값이 그대로 운영에 나간다.
        AdminTokenProvider disabled = provider("", Instant.parse("2026-08-20T00:00:00Z"));

        assertThat(disabled.isEnabled()).isFalse();
        assertThat(disabled.parse("아무값")).isNull();
    }
}
