package com.pawever.backend.goodssurvey.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public interface GoodsSurveyPhotoStorage {

    PresignedUpload presignUpload(
            String objectKey,
            String contentType,
            long contentLength,
            Duration duration,
            Instant expiresAt
    );

    StoredObject head(String objectKey);

    /** 제작에 넘길 사진을 원본 그대로 읽는다. */
    byte[] download(String objectKey);

    record PresignedUpload(
            String url,
            Map<String, String> headers,
            Instant expiresAt
    ) {
    }

    record StoredObject(
            long contentLength,
            String contentType,
            byte[] signatureBytes
    ) {
    }
}
