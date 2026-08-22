package com.pawever.backend.goodssurvey.service;

import com.pawever.backend.global.security.HmacHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

/**
 * 수신거부 링크에 실리는 값.
 *
 * 주소를 그대로 싣지 않는다. 메일에 {@code ?email=someone@example.com} 을
 * 넣으면 그 주소가 링크를 거치는 모든 곳에 남고, 남의 주소를 적어 넣어
 * 대신 해지시킬 수도 있다.
 *
 * 대신 구독 번호에 서명을 붙여 보낸다. 키를 모르면 만들 수 없고, 열어 봐도
 * 번호밖에 없다. 저장할 칸을 새로 만들지 않아도 된다.
 *
 * emailHash 를 그대로 쓰지 않는다. 그 값은 같은 주소가 두 번 들어왔는지
 * 보려고 두는 내부 값이다. 밖에 내보내면 링크를 본 사람이 그 값으로 다른
 * 것을 물어볼 수 있게 된다.
 */
@Component
@RequiredArgsConstructor
public class GoodsSurveyUnsubscribeToken {

    private static final String PURPOSE = "goods-notice-unsubscribe:";
    private static final String SEPARATOR = ":";

    private final HmacHasher hmacHasher;

    public String issue(Long subscriptionId) {
        String raw = subscriptionId + SEPARATOR + signature(subscriptionId);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 서명이 맞으면 구독 번호를, 아니면 비어 있는 값을 돌려준다. */
    public Optional<Long> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            String raw = new String(
                    Base64.getUrlDecoder().decode(token.trim()), StandardCharsets.UTF_8);
            int boundary = raw.indexOf(SEPARATOR);
            if (boundary <= 0) {
                return Optional.empty();
            }
            long subscriptionId = Long.parseLong(raw.substring(0, boundary));
            String provided = raw.substring(boundary + 1);

            // 앞에서부터 한 글자씩 비교하면 어디까지 맞았는지가 시간으로 드러난다.
            if (!MessageDigest.isEqual(
                    provided.getBytes(StandardCharsets.UTF_8),
                    signature(subscriptionId).getBytes(StandardCharsets.UTF_8))) {
                return Optional.empty();
            }
            return Optional.of(subscriptionId);
        } catch (IllegalArgumentException exception) {
            // Base64 가 아니거나 번호 자리가 숫자가 아니다. 어느 쪽이든 우리가 만든 값이 아니다.
            return Optional.empty();
        }
    }

    /**
     * 쓰임새를 앞에 붙인다.
     *
     * 같은 키로 만든 다른 값을 이 자리에 가져다 쓰지 못하게 한다.
     */
    private String signature(Long subscriptionId) {
        return hmacHasher.hash(PURPOSE + subscriptionId);
    }
}
