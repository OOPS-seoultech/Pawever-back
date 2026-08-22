package com.pawever.backend.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 토스페이먼츠 설정.
 *
 * 시크릿 키는 서버에만 둔다. 이 키 하나로 남의 결제를 조회하고 취소할 수
 * 있으므로, 화면에 내려보내는 값과 같은 곳에 두면 안 된다.
 *
 * 클라이언트 키는 화면이 결제창을 여는 데 쓴다. 밖에 나가는 것이 정상이다.
 *
 * 키를 설정하지 않으면 결제가 아예 열리지 않는다. 기본값을 두면 그 값이
 * 그대로 운영에 나간다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "payment.toss", ignoreUnknownFields = false)
public class TossPaymentsProperties {

    /** 화면이 결제창을 여는 데 쓰는 값. 밖에 나가도 되는 값이다. */
    private String clientKey = "";

    /** 승인·조회·취소에 쓰는 값. 서버 밖으로 나가면 안 된다. */
    private String secretKey = "";

    private String baseUrl = "https://api.tosspayments.com";

    /** 키가 없으면 결제 통로 전체가 닫힌 것으로 본다. */
    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }
}
