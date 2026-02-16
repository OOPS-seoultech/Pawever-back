package com.pawever.backend.global.common;

import com.pawever.backend.global.config.NcpStorageConfig;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
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
            log.error("파일 업로드 실패: {}", originalFilename, e);
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        return ncpStorageConfig.getCdn().getDomain() + "/" + key;
    }

    public void delete(String fileUrl) {
        String cdnDomain = ncpStorageConfig.getCdn().getDomain();
        if (fileUrl == null || !fileUrl.startsWith(cdnDomain)) {
            return;
        }

        String key = fileUrl.substring(cdnDomain.length() + 1);

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
