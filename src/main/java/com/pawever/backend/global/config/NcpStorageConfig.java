package com.pawever.backend.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Getter
@Setter
@Configuration
@ConfigurationProperties("cloud.ncp")
public class NcpStorageConfig {

    private Credentials credentials;
    private String region;
    private S3Properties s3;
    private CdnProperties cdn;

    @Getter
    @Setter
    public static class Credentials {
        private String accessKey;
        private String secretKey;
    }

    @Getter
    @Setter
    public static class S3Properties {
        private String endpoint;
        private String bucket;
    }

    @Getter
    @Setter
    public static class CdnProperties {
        private String domain;
    }

    @Bean
    public S3Client s3Client() {
        // 공통 설정: region + 자격증명
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                credentials.getAccessKey(),
                                credentials.getSecretKey()
                        )
                ));
        // endpoint가 지정된 경우(NCP 등 S3 호환 스토리지)에만 override + path-style 적용.
        // AWS S3는 endpoint를 비워두면 기본 엔드포인트(virtual-hosted style)를 사용.
        if (s3.getEndpoint() != null && !s3.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(s3.getEndpoint()))
                    .forcePathStyle(true);
        }
        return builder.build();
    }
}
