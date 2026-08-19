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

    /**
     * 보유 기간이 지난 사진을 지운다.
     *
     * 이미 없는 키는 지운 것으로 본다. 파기는 매일 도는 작업이라, 한 건이
     * 걸려 넘어지면 뒤에 밀린 건들이 함께 못 지워진다.
     */
    void delete(String objectKey);

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
