package com.pawever.backend.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class NaverApiClient {

    private static final String USER_ME_URL = "https://openapi.naver.com/v1/nid/me";

    private final RestTemplate restTemplate;

    public NaverUserInfo getUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        try {
            ResponseEntity<NaverUserInfoResponse> response = restTemplate.exchange(
                    USER_ME_URL,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    NaverUserInfoResponse.class
            );
            NaverUserInfoResponse body = response.getBody();
            if (body == null || body.getResponse() == null) {
                throw new CustomException(ErrorCode.NAVER_API_ERROR);
            }
            return body.getResponse();
        } catch (RestClientException e) {
            throw new CustomException(ErrorCode.NAVER_API_ERROR);
        }
    }

    @Getter
    public static class NaverUserInfoResponse {

        private String resultcode;
        private String message;
        private NaverUserInfo response;
    }

    @Getter
    public static class NaverUserInfo {

        private String id;
        private String nickname;
        private String name;
        private String email;

        @JsonProperty("profile_image")
        private String profileImage;
    }
}
