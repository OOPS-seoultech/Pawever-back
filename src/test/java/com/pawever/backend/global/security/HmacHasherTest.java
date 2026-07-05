package com.pawever.backend.global.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class HmacHasherTest {

    private String base64Key(String raw) {
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void hash_isDeterministicForSameInput() {
        HmacHasher hasher = new HmacHasher(base64Key("hash-key-for-blind-index-32bytes"));

        assertEquals(hasher.hash("010-1234-5678"), hasher.hash("010-1234-5678"));
    }

    @Test
    void constructor_whenKeyShorterThan32Bytes_throws() {
        assertThrows(IllegalArgumentException.class, () -> new HmacHasher(base64Key("short-key")));
    }
}
