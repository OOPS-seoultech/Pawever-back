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

            private String name;

            /** 전화번호: "+82 10-1234-5678" 형식 */
            @JsonProperty("phone_number")
            private String phoneNumber;

            /** 성별: "male" | "female" */
            private String gender;

            /** 생일: "MMDD" */
            private String birthday;

            /** 출생연도: "YYYY" */
            private String birthyear;

            /** 연령대: "20~29" */
            @JsonProperty("age_range")
            private String ageRange;

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

        public String getName() {
            if (kakaoAccount == null) return null;
            return kakaoAccount.getName();
        }

        public String getNickname() {
            if (kakaoAccount == null || kakaoAccount.getProfile() == null) return null;
            return kakaoAccount.getProfile().getNickname();
        }

        public String getProfileImageUrl() {
            if (kakaoAccount == null || kakaoAccount.getProfile() == null) return null;
            return kakaoAccount.getProfile().getProfileImageUrl();
        }

        public String getEmail() {
            if (kakaoAccount == null) return null;
            return kakaoAccount.getEmail();
        }

        /**
         * 카카오 전화번호 "+82 10-1234-5678" → "010-1234-5678" 변환
         */
        public String getPhone() {
            if (kakaoAccount == null || kakaoAccount.getPhoneNumber() == null) return null;
            String raw = kakaoAccount.getPhoneNumber().trim();
            if (raw.startsWith("+82 ")) {
                return "0" + raw.substring(4);
            }
            return raw;
        }

        public String getGender() {
            if (kakaoAccount == null) return null;
            return kakaoAccount.getGender();
        }

        public String getBirthday() {
            if (kakaoAccount == null) return null;
            return kakaoAccount.getBirthday();
        }

        public String getBirthYear() {
            if (kakaoAccount == null) return null;
            return kakaoAccount.getBirthyear();
        }

        public String getAgeRange() {
            if (kakaoAccount == null) return null;
            return kakaoAccount.getAgeRange();
        }
    }
}
