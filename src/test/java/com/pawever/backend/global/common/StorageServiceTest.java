package com.pawever.backend.global.common;

import com.pawever.backend.global.config.NcpStorageConfig;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock private S3Client s3Client;
    @Mock private NcpStorageConfig ncpStorageConfig;

    @InjectMocks
    private StorageService storageService;

    @Test
    void upload_whenSvg_throwsUnsupportedFileType_andDoesNotTouchS3() {
        MockMultipartFile svg = new MockMultipartFile(
                "file", "x.svg", "image/svg+xml", "<svg onload=alert(1)/>".getBytes());

        CustomException ex = assertThrows(CustomException.class,
                () -> storageService.upload(svg, "reviews/1"));

        assertEquals(ErrorCode.UNSUPPORTED_FILE_TYPE, ex.getErrorCode());
        verifyNoInteractions(s3Client);
    }

    @Test
    void upload_whenPng_forcesWhitelistedContentTypeEvenIfClientLies() {
        NcpStorageConfig.S3Properties s3 = new NcpStorageConfig.S3Properties();
        s3.setBucket("bucket");
        NcpStorageConfig.CdnProperties cdn = new NcpStorageConfig.CdnProperties();
        cdn.setDomain("cdn.example.com");
        when(ncpStorageConfig.getS3()).thenReturn(s3);
        when(ncpStorageConfig.getCdn()).thenReturn(cdn);

        // 클라이언트가 content-type을 text/html로 위조해도, 확장자 기반 안전한 타입으로 저장돼야 한다.
        MockMultipartFile png = new MockMultipartFile(
                "file", "photo.PNG", "text/html", new byte[]{1, 2, 3});

        String url = storageService.upload(png, "pets/1/profile");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertEquals("image/png", captor.getValue().contentType());
        assertTrue(url.endsWith(".png"));
    }
}
