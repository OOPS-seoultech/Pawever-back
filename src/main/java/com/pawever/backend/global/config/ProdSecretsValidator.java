package com.pawever.backend.global.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Set;

/**
 * 운영(prod) 기동 시 보안 시크릿이 실제로 주입됐는지 검증한다.
 * 소스에 커밋된 공개 기본값이거나 비어 있으면 즉시 기동을 실패(fail-fast)시켜,
 * 공개된 키로 JWT 서명·PII 암호화가 이뤄지는 사고를 예방한다.
 * (@Profile("prod")이므로 로컬/테스트에는 로드되지 않아 개발 편의는 유지된다.)
 */
@Slf4j
@Profile("prod")
@Configuration
public class ProdSecretsValidator {

    // application.yaml 에 커밋돼 있던 공개 기본값 — 운영에서 이 값이 쓰이면 안 된다.
    private static final Set<String> INSECURE_DEFAULTS = Set.of(
            "pawever-jwt-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm",
            "cGF3ZXZlci1hZXMtMjU2LWtleS0zMmJ5dGVzLWxvbmc=",
            "aGFzaC1rZXktZm9yLWJsaW5kLWluZGV4LTMyYnl0ZXM="
    );

    private final String jwtSecret;
    private final String encryptionSecretKey;
    private final String encryptionHashKey;

    public ProdSecretsValidator(
            @Value("${jwt.secret:}") String jwtSecret,
            @Value("${encryption.secret-key:}") String encryptionSecretKey,
            @Value("${encryption.hash-key:}") String encryptionHashKey
    ) {
        this.jwtSecret = jwtSecret;
        this.encryptionSecretKey = encryptionSecretKey;
        this.encryptionHashKey = encryptionHashKey;
    }

    @PostConstruct
    void validate() {
        requireSecure("jwt.secret", jwtSecret);
        requireSecure("encryption.secret-key", encryptionSecretKey);
        requireSecure("encryption.hash-key", encryptionHashKey);
        log.info("[ProdSecretsValidator] 운영 시크릿 검증 통과");
    }

    private void requireSecure(String name, String value) {
        if (value == null || value.isBlank() || INSECURE_DEFAULTS.contains(value)) {
            throw new IllegalStateException(
                    name + " 이(가) 설정되지 않았거나 소스에 공개된 기본값입니다. "
                            + "운영 환경에서는 안전한 시크릿을 환경변수로 주입하세요.");
        }
    }
}
