package com.pawever.backend.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AppleApiClient {

    private static final String APPLE_KEYS_URL = "https://appleid.apple.com/auth/keys";
    private static final String APPLE_TOKEN_URL = "https://appleid.apple.com/auth/token";
    private static final String APPLE_REVOKE_URL = "https://appleid.apple.com/auth/revoke";
    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate;

    @Value("${apple.bundle-id}")
    private String bundleId;

    @Value("${apple.team-id}")
    private String teamId;

    @Value("${apple.key-id}")
    private String keyId;

    /**
     * .p8 파일에서 헤더/푸터를 제거하고 개행을 제거한 base64 문자열.
     * ex) MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0w...
     */
    @Value("${apple.private-key}")
    private String privateKeyContent;

    // ── identityToken 검증 ──────────────────────────────────────────────────

    public AppleUserInfo getUserInfo(String identityToken) {
        String kid = extractKid(identityToken);
        ApplePublicKey appleKey = fetchPublicKey(kid);
        PublicKey publicKey = buildPublicKey(appleKey);
        Claims claims = parseAndValidateClaims(identityToken, publicKey);
        return new AppleUserInfo(claims.getSubject(), claims.get("email", String.class));
    }

    private String extractKid(String identityToken) {
        try {
            String headerJson = new String(Base64.getUrlDecoder().decode(identityToken.split("\\.")[0]));
            @SuppressWarnings("unchecked")
            Map<String, String> header = OBJECT_MAPPER.readValue(headerJson, Map.class);
            String kid = header.get("kid");
            if (kid == null) throw new CustomException(ErrorCode.APPLE_TOKEN_INVALID);
            return kid;
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException(ErrorCode.APPLE_TOKEN_INVALID);
        }
    }

    private ApplePublicKey fetchPublicKey(String kid) {
        try {
            AppleKeysResponse body = restTemplate.getForObject(APPLE_KEYS_URL, AppleKeysResponse.class);
            if (body == null || body.getKeys() == null) {
                throw new CustomException(ErrorCode.APPLE_API_ERROR);
            }
            return body.getKeys().stream()
                    .filter(k -> kid.equals(k.getKid()))
                    .findFirst()
                    .orElseThrow(() -> new CustomException(ErrorCode.APPLE_TOKEN_INVALID));
        } catch (CustomException e) {
            throw e;
        } catch (RestClientException e) {
            throw new CustomException(ErrorCode.APPLE_API_ERROR);
        }
    }

    private PublicKey buildPublicKey(ApplePublicKey key) {
        try {
            BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(key.getN()));
            BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(key.getE()));
            return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
        } catch (Exception e) {
            throw new CustomException(ErrorCode.APPLE_TOKEN_INVALID);
        }
    }

    private Claims parseAndValidateClaims(String identityToken, PublicKey publicKey) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(identityToken)
                    .getPayload();

            if (!APPLE_ISSUER.equals(claims.getIssuer())) {
                throw new CustomException(ErrorCode.APPLE_TOKEN_INVALID);
            }
            Object aud = claims.get("aud");
            boolean audValid = bundleId.equals(aud)
                    || (aud instanceof Collection<?> c && c.contains(bundleId));
            if (!audValid) {
                throw new CustomException(ErrorCode.APPLE_TOKEN_INVALID);
            }
            return claims;
        } catch (ExpiredJwtException e) {
            throw new CustomException(ErrorCode.EXPIRED_TOKEN);
        } catch (CustomException e) {
            throw e;
        } catch (JwtException e) {
            throw new CustomException(ErrorCode.APPLE_TOKEN_INVALID);
        }
    }

    // ── authorizationCode → refresh_token 교환 ─────────────────────────────

    public String exchangeAuthCode(String authorizationCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", bundleId);
        body.add("client_secret", generateClientSecret());
        body.add("code", authorizationCode);
        body.add("grant_type", "authorization_code");

        try {
            AppleTokenResponse response = restTemplate.postForObject(
                    APPLE_TOKEN_URL, new HttpEntity<>(body, headers), AppleTokenResponse.class
            );
            if (response == null || response.getRefreshToken() == null) {
                throw new CustomException(ErrorCode.APPLE_API_ERROR);
            }
            return response.getRefreshToken();
        } catch (CustomException e) {
            throw e;
        } catch (RestClientException e) {
            throw new CustomException(ErrorCode.APPLE_API_ERROR);
        }
    }

    // ── refresh_token 취소 (탈퇴 시 호출) ─────────────────────────────────

    public void revokeToken(String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", bundleId);
        body.add("client_secret", generateClientSecret());
        body.add("token", refreshToken);
        body.add("token_type_hint", "refresh_token");

        try {
            restTemplate.postForObject(
                    APPLE_REVOKE_URL, new HttpEntity<>(body, headers), Void.class
            );
        } catch (RestClientException e) {
            throw new CustomException(ErrorCode.APPLE_API_ERROR);
        }
    }

    // ── client_secret JWT 생성 ──────────────────────────────────────────────

    private String generateClientSecret() {
        PrivateKey privateKey = loadPrivateKey();
        Instant now = Instant.now();
        return Jwts.builder()
                .header().add("kid", keyId).and()
                .issuer(teamId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(180, ChronoUnit.DAYS)))
                .claim("aud", APPLE_ISSUER)
                .subject(bundleId)
                .signWith(privateKey)
                .compact();
    }

    private PrivateKey loadPrivateKey() {
        try {
            String cleaned = privateKeyContent
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] keyBytes = Base64.getDecoder().decode(cleaned);
            return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new CustomException(ErrorCode.APPLE_TOKEN_INVALID);
        }
    }

    // ── inner classes ───────────────────────────────────────────────────────

    @Getter
    public static class AppleKeysResponse {
        private List<ApplePublicKey> keys;
    }

    @Getter
    public static class ApplePublicKey {
        private String kty;
        private String kid;
        private String use;
        private String alg;
        private String n;
        private String e;
    }

    @Getter
    public static class AppleTokenResponse {
        @JsonProperty("access_token")
        private String accessToken;
        @JsonProperty("refresh_token")
        private String refreshToken;
        @JsonProperty("id_token")
        private String idToken;
        @JsonProperty("expires_in")
        private Long expiresIn;
    }

    public record AppleUserInfo(String appleId, String email) {}
}
