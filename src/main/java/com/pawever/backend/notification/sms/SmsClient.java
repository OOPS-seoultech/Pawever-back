package com.pawever.backend.notification.sms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 알리고로 문자 한 통을 보낸다.
 *
 * 알리고는 실패를 HTTP 상태로 알려 주지 않는다. 키가 틀려도, 발신번호가
 * 등록돼 있지 않아도, 잔액이 없어도 200 에 result_code 를 음수로 담아 준다.
 * 그래서 상태 코드만 보면 안 나간 문자를 나갔다고 세게 된다.
 *
 * 텔레그램과 달리 이 문자는 "알림"이 아니라 결제 수단이다. 이것이 못 나가면
 * 신청자는 낼 방법이 없고 주문은 시간이 지나 사라진다. 그래도 예외를 밖으로
 * 던지지는 않는다 — 접수는 이미 커밋되었고, 여기서 터뜨리면 접수까지 실패한
 * 것처럼 보인다. 대신 반드시 로그를 남겨 사람이 이어받을 수 있게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsClient {

    /** 성공. 알리고는 이 값이 1 일 때만 접수된 것으로 본다. */
    private static final int RESULT_OK = 1;

    private final RestTemplate restTemplate;
    private final SmsProperties properties;

    /**
     * 긴 문자(LMS)로 보낸다.
     *
     * 계좌·금액·기한을 담으면 단문 한도(EUC-KR 90바이트)를 넘는다. SMS 로
     * 보내면 알리고가 뒤를 잘라 계좌번호가 끊긴 문자가 나간다.
     *
     * @return 실제로 접수되었으면 true.
     */
    public boolean sendLms(String receiver, String title, String message) {
        if (!properties.isConfigured()) {
            log.debug("문자 설정이 없어 입금 안내를 보내지 않는다");
            return false;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("key", properties.getApiKey());
        form.add("user_id", properties.getUserId());
        form.add("sender", properties.getSender());
        form.add("receiver", digitsOnly(receiver));
        form.add("msg", message);
        form.add("title", title);
        form.add("msg_type", "LMS");
        form.add("testmode_yn", properties.isTestMode() ? "Y" : "N");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            ResponseEntity<AligoResponse> response = restTemplate.postForEntity(
                    properties.getBaseUrl() + "/send/",
                    new HttpEntity<>(form, headers),
                    AligoResponse.class
            );
            AligoResponse body = response.getBody();
            if (body == null || body.resultCode() != RESULT_OK) {
                // 받는 번호는 남기지 않는다. 실패를 남기려다 연락처를 로그에 흘린다.
                log.error(
                        "입금 안내 문자 거절: code={} message={}",
                        body == null ? "(없음)" : body.resultCode(),
                        body == null ? "(응답 없음)" : body.message()
                );
                return false;
            }
            return true;
        } catch (RestClientException e) {
            log.error("입금 안내 문자 전송 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 번호에서 숫자만 남긴다.
     *
     * 신청 화면은 자리표시자가 010-0000-0000 이라 대부분 하이픈을 넣어 적는다.
     * 알리고는 하이픈이 섞인 번호를 받아 주기도 하고 거절하기도 해서, 보내기
     * 전에 우리가 형태를 하나로 만든다. 여기서 어긋나면 실패도 아니고 성공도
     * 아닌 채로 문자만 안 간다.
     */
    private static String digitsOnly(String phone) {
        return phone == null ? "" : phone.replaceAll("[^0-9]", "");
    }
}
