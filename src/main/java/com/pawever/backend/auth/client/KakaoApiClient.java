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
public class KakaoApiClient {

    private static final String USER_ME_URL = "https://kapi.kakao.com/v2/user/me";

    private final RestTemplate restTemplate;

    public KakaoUserInfo getUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            ResponseEntity<KakaoUserInfo> response = restTemplate.exchange(
                    USER_ME_URL,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    KakaoUserInfo.class
            );
            return response.getBody();
        } catch (RestClientException e) {
            throw new CustomException(ErrorCode.KAKAO_API_ERROR);
        }
    }

    @Getter
    public static class KakaoUserInfo {

        private Long id;

        @JsonProperty("kakao_account")
        private KakaoAccount kakaoAccount;

        @Getter
        public static class KakaoAccount {

            private String email;

            @JsonProperty("profile")
            private Profile profile;

            @Getter
            public static class Profile {

                private String nickname;

                @JsonProperty("profile_image_url")
                private String profileImageUrl;
            }
        }

        public String getKakaoId() {
            return String.valueOf(id);
        }

        public String getNickname() {
            if (kakaoAccount == null || kakaoAccount.getProfile() == null) return null;
            return kakaoAccount.getProfile().getNickname();
        }

        public String getProfileImageUrl() {
            if (kakaoAccount == null || kakaoAccount.getProfile() == null) return null;
            return kakaoAccount.getProfile().getProfileImageUrl();
        }
    }
}
