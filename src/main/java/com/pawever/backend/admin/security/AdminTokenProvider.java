package com.pawever.backend.admin.security;

import com.pawever.backend.admin.entity.AdminRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import com.pawever.backend.admin.config.AdminProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;

/**
 * 관리자 로그인 토큰.
 *
 * 앱 회원 토큰과 서명 키를 나눈다. 같은 키를 쓰면 앱 토큰 하나로 관리자
 * 요청을 통과시킬 수 있는지가 claim 검사 하나에만 달리게 된다. 키가 다르면
 * 검사를 빠뜨려도 서명 단계에서 걸린다.
 *
 * 키를 설정하지 않으면 관리자 로그인이 아예 열리지 않는다. 기본값을 두면
 * 그 값이 그대로 운영에 나간다.
 */
@Component
public class AdminTokenProvider {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final long expirationMillis;
    private final Clock clock;

    public AdminTokenProvider(AdminProperties properties, Clock clock) {
        String secret = properties.getJwtSecret();
        this.key = secret == null || secret.isBlank()
                ? null
                : Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = properties.getJwtExpirationMillis();
        this.clock = clock;
    }

    /** 키가 없으면 관리자 도메인 전체가 닫힌 것으로 본다. */
    public boolean isEnabled() {
        return key != null;
    }

    public String createToken(Long adminAccountId, AdminRole role) {
        long now = clock.millis();
        return Jwts.builder()
                .subject(String.valueOf(adminAccountId))
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMillis))
                .signWith(key)
                .compact();
    }

    /** 서명과 만료를 확인하고 담긴 내용을 돌려준다. 아니면 null. */
    public AdminPrincipal parse(String token) {
        if (key == null) {
            return null;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    // 만료 판정도 주입한 시계를 따르게 한다. 기본값은 시스템 시계라
                    // 시간을 옮겨 가며 확인할 방법이 없다.
                    .clock(() -> new Date(clock.millis()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new AdminPrincipal(
                    Long.parseLong(claims.getSubject()),
                    AdminRole.valueOf(claims.get(ROLE_CLAIM, String.class))
            );
        } catch (Exception exception) {
            // 서명이 다르거나 만료됐거나 역할 이름이 바뀐 토큰. 어느 쪽이든 통과시키지 않는다.
            return null;
        }
    }

    public long expirationMillis() {
        return expirationMillis;
    }
}
