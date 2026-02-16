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
        return S3Client.builder()
                .endpointOverride(URI.create(s3.getEndpoint()))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                credentials.getAccessKey(),
                                credentials.getSecretKey()
                        )
                ))
                .forcePathStyle(true)
                .build();
    }
}
