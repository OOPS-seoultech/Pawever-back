package com.pawever.backend.global.common;

import com.pawever.backend.global.config.NcpStorageConfig;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.global.util.UrlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Client s3Client;
    private final NcpStorageConfig ncpStorageConfig;

    public String upload(MultipartFile file, String dirPath) {
        String originalFilename = file.getOriginalFilename();
        String extension = extractExtension(originalFilename);
        String key = dirPath + "/" + UUID.randomUUID() + extension;

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(ncpStorageConfig.getS3().getBucket())
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
        } catch (IOException e) {
            log.error(
                    "파일 바이트 읽기 실패. bucket={}, key={}, originalFilename={}, contentType={}, size={}",
                    ncpStorageConfig.getS3().getBucket(),
                    key,
                    originalFilename,
                    file.getContentType(),
                    file.getSize(),
                    e
            );
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
        } catch (Exception e) {
            log.error(
                    "오브젝트 스토리지 업로드 실패. bucket={}, key={}, originalFilename={}, contentType={}, size={}",
                    ncpStorageConfig.getS3().getBucket(),
                    key,
                    originalFilename,
                    file.getContentType(),
                    file.getSize(),
                    e
            );
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        String cdnDomain = UrlUtils.trimTrailingSlash(ncpStorageConfig.getCdn().getDomain());
        return UrlUtils.toHttpsUrl(cdnDomain) + "/" + key;
    }

    public void delete(String fileUrl) {
        String cdnDomain = UrlUtils.stripScheme(UrlUtils.trimTrailingSlash(ncpStorageConfig.getCdn().getDomain()));
        String normalizedFileUrl = UrlUtils.stripScheme(fileUrl);

        if (cdnDomain == null || cdnDomain.isBlank() || normalizedFileUrl == null) {
            return;
        }

        String prefix = cdnDomain + "/";
        if (!normalizedFileUrl.startsWith(prefix)) {
            return;
        }

        String key = normalizedFileUrl.substring(prefix.length());

        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(ncpStorageConfig.getS3().getBucket())
                    .key(key)
                    .build();

            s3Client.deleteObject(request);
        } catch (Exception e) {
            log.warn("파일 삭제 실패: {}", key, e);
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}
