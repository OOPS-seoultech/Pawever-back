package com.pawever.backend.goodssurvey.service;

import java.nio.charset.StandardCharsets;

final class GoodsSurveyImageSignature {

    private GoodsSurveyImageSignature() {
    }

    static boolean matches(String contentType, byte[] bytes) {
        if (bytes == null) return false;
        return switch (contentType) {
            case "image/jpeg" -> startsWith(bytes, new byte[]{
                    (byte) 0xff, (byte) 0xd8, (byte) 0xff
            });
            case "image/png" -> startsWith(bytes, new byte[]{
                    (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
            });
            case "image/webp" -> bytes.length >= 12
                    && "RIFF".equals(new String(bytes, 0, 4, StandardCharsets.US_ASCII))
                    && "WEBP".equals(new String(bytes, 8, 4, StandardCharsets.US_ASCII));
            default -> false;
        };
    }

    private static boolean startsWith(byte[] bytes, byte[] signature) {
        if (bytes.length < signature.length) return false;
        for (int index = 0; index < signature.length; index++) {
            if (bytes[index] != signature[index]) return false;
        }
        return true;
    }
}
