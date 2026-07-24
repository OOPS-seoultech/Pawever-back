package com.pawever.backend.goodssurvey.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GoodsSurveyImageSignatureTest {

    @Test
    void validatesActualImageSignatureInsteadOfTrustingTheContentTypeHeader() {
        assertThat(GoodsSurveyImageSignature.matches(
                "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00}
        )).isTrue();
        assertThat(GoodsSurveyImageSignature.matches(
                "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}
        )).isTrue();
        assertThat(GoodsSurveyImageSignature.matches(
                "image/webp",
                "RIFF0000WEBP".getBytes(StandardCharsets.US_ASCII)
        )).isTrue();
        assertThat(GoodsSurveyImageSignature.matches(
                "image/jpeg",
                "<script>".getBytes(StandardCharsets.UTF_8)
        )).isFalse();
    }
}
