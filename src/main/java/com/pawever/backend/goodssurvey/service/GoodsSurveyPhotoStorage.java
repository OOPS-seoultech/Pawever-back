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
     * 담당자에게 내려줄 짧은 만료 링크를 만든다.
     *
     * 사진은 비공개 저장소에 있어 주소만으로는 열리지 않는다. 인증을 거친
     * 담당자에게만, 그것도 잠깐 동안만 열리는 주소를 준다. 만료가 길면
     * 링크가 메신저를 타고 돌아다니는 순간 인증을 거친 뜻이 없어진다.
     */
    PresignedDownload presignDownload(String objectKey, Duration duration, Instant expiresAt);

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

    record PresignedDownload(
            String url,
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
