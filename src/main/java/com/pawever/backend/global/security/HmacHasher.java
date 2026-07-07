package com.pawever.backend.global.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class HmacHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] hashKey;

    public HmacHasher(@Value("${encryption.hash-key}") String base64Key) {
        this.hashKey = Base64.getDecoder().decode(base64Key);
        if (hashKey.length < 32) {
            throw new IllegalArgumentException("HMAC 키는 최소 32바이트(Base64 인코딩)여야 합니다.");
        }
    }

    public String hash(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(hashKey, ALGORITHM));
            return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("해시 생성 실패", e);
        }
    }
}
