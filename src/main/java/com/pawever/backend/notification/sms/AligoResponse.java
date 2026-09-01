package com.pawever.backend.notification.sms;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 알리고 발송 응답.
 *
 * 성공도 실패도 HTTP 200 으로 온다. 갈리는 것은 result_code 하나다 —
 * 1 이면 접수, 음수면 거절이고 message 에 이유가 담긴다. 자주 보게 될 값은
 * 키가 틀렸을 때, 발신번호가 등록돼 있지 않을 때, 잔액이 없을 때다.
 *
 * 알리고는 문서에 없는 필드를 상황에 따라 더 실어 준다. 모르는 필드에
 * 터지면 실제로는 나간 문자를 실패로 세게 되므로 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AligoResponse(
        @JsonProperty("result_code") int resultCode,
        @JsonProperty("message") String message,
        @JsonProperty("msg_id") String msgId
) {
}
