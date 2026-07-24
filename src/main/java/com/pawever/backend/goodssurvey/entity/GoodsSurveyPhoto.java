package com.pawever.backend.goodssurvey.entity;

import com.pawever.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "goods_survey_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoodsSurveyPhoto extends BaseTimeEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String responseId;

    @Column(nullable = false, length = 36)
    private String clientFileId;

    @Column(nullable = false, unique = true, length = 500)
    private String objectKey;

    @Column(nullable = false, length = 50)
    private String contentType;

    @Column(nullable = false)
    private long expectedSize;

    private Long actualSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoodsSurveyPhotoStatus status;

    @Column(nullable = false)
    private Instant uploadExpiresAt;

    private Instant confirmedAt;

    public static GoodsSurveyPhoto pending(
            String id,
            String responseId,
            String clientFileId,
            String objectKey,
            String contentType,
            long expectedSize,
            Instant uploadExpiresAt
    ) {
        GoodsSurveyPhoto photo = new GoodsSurveyPhoto();
        photo.id = id;
        photo.responseId = responseId;
        photo.clientFileId = clientFileId;
        photo.objectKey = objectKey;
        photo.contentType = contentType;
        photo.expectedSize = expectedSize;
        photo.status = GoodsSurveyPhotoStatus.PENDING;
        photo.uploadExpiresAt = uploadExpiresAt;
        return photo;
    }

    public void renewUpload(Instant uploadExpiresAt) {
        this.uploadExpiresAt = uploadExpiresAt;
    }

    public void confirm(long actualSize, Instant confirmedAt) {
        this.actualSize = actualSize;
        this.status = GoodsSurveyPhotoStatus.CONFIRMED;
        this.confirmedAt = confirmedAt;
    }
}
