package com.pawever.backend.goodssurvey.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GoodsSurveyPhotoTest {

    @Test
    void storesPublicationConsentSeparatelyForEachPhoto() {
        GoodsSurveyPhoto photo = GoodsSurveyPhoto.pending(
                "photo-1",
                "response-1",
                "client-file-1",
                "goods-survey/response-1/photo-1.jpg",
                "image/jpeg",
                1024,
                Instant.parse("2026-07-25T10:00:00Z")
        );

        assertThat(photo.isPublicationAgreed()).isFalse();

        photo.setPublicationAgreed(true);

        assertThat(photo.isPublicationAgreed()).isTrue();
    }
}
