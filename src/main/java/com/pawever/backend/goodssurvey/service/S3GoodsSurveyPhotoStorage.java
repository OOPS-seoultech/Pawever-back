package com.pawever.backend.goodssurvey.service;

import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.goodssurvey.config.GoodsSurveyProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.core.sync.ResponseTransformer;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class S3GoodsSurveyPhotoStorage implements GoodsSurveyPhotoStorage {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final GoodsSurveyProperties properties;

    @Override
    public PresignedUpload presignUpload(
            String objectKey,
            String contentType,
            long contentLength,
            Duration duration,
            Instant expiresAt
    ) {
        String bucket = privateBucket();
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength(contentLength)
                    .build();
            var presigned = s3Presigner.presignPutObject(
                    PutObjectPresignRequest.builder()
                            .signatureDuration(duration)
                            .putObjectRequest(putObjectRequest)
                            .build()
            );
            return new PresignedUpload(
                    presigned.url().toString(),
                    Map.of("Content-Type", contentType),
                    expiresAt
            );
        } catch (Exception exception) {
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    public StoredObject head(String objectKey) {
        try {
            var result = s3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(privateBucket())
                            .key(objectKey)
                            .build()
            );
            byte[] signature = s3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(privateBucket())
                            .key(objectKey)
                            .range("bytes=0-15")
                            .build(),
                    ResponseTransformer.toBytes()
            ).asByteArray();
            return new StoredObject(
                    result.contentLength(),
                    result.contentType(),
                    signature
            );
        } catch (CustomException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CustomException(ErrorCode.SURVEY_PHOTO_NOT_READY);
        }
    }

    private String privateBucket() {
        String bucket = properties.getPhotoBucket();
        if (bucket == null || bucket.isBlank()) {
            throw new CustomException(ErrorCode.SURVEY_STORAGE_NOT_CONFIGURED);
        }
        return bucket;
    }
}
