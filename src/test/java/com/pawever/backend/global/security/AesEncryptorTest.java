package com.pawever.backend.global.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class AesEncryptorTest {

    // 32바이트 키의 Base64 인코딩
    private final AesEncryptor encryptor = new AesEncryptor(
            Base64.getEncoder().encodeToString("pawever-aes-256-key-32bytes-long".getBytes(StandardCharsets.UTF_8)));

    @Test
    void encryptThenDecrypt_roundTripsKoreanText() {
        String plain = "홍길동";

        String encrypted = encryptor.encrypt(plain);

        assertNotEquals(plain, encrypted);
        assertEquals(plain, encryptor.decrypt(encrypted)); // charset 명시로 한글 왕복 보장
    }

    @Test
    void encrypt_producesDifferentCiphertextEachTime_dueToRandomIv() {
        String plain = "010-1234-5678";

        assertNotEquals(encryptor.encrypt(plain), encryptor.encrypt(plain));
    }
}
