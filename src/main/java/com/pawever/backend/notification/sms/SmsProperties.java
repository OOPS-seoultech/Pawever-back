package com.pawever.backend.notification.sms;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 입금 안내 문자 설정.
 *
 * 무통장 입금으로 받기로 한 이상, 계좌를 전달하는 길이 곧 결제 수단이다.
 * 그 길이 없으면 신청자는 낼 방법이 없고 주문은 시간이 지나 만료된다.
 * 실제로 2026-08-30 접수된 첫 주문이 계좌를 받지 못한 채 만료됐다.
 *
 * 계좌는 화면에 적지 않고 문자로만 보낸다. 화면에 붙여 두면 신청하지 않은
 * 사람도 입금하게 되고, 그러면 누가 어떤 주문 값을 냈는지 대조할 수 없다.
 *
 * 값은 전부 서버 환경변수로 들어온다. 계좌번호는 비밀은 아니지만 저장소에
 * 두면 바뀔 때마다 배포가 필요하고, 실수로 다른 계좌가 나가면 돈이 엉뚱한
 * 곳으로 간다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "notification.sms", ignoreUnknownFields = false)
public class SmsProperties {

    /** 알리고 API 키. 2026-08-16 에 팀이 가입하고 비용을 충전해 둔 계정이다. */
    private String apiKey = "";

    /** 알리고 계정 아이디. 키와 짝이라 둘 중 하나만 있으면 보낼 수 없다. */
    private String userId = "";

    /**
     * 발신번호.
     *
     * 통신사에 사전 등록된 번호만 쓸 수 있다. 등록되지 않은 번호로 보내면
     * 알리고가 거절한다 — 키가 맞아도 여기서 막힌다.
     */
    private String sender = "";

    private String baseUrl = "https://apis.aligo.in";

    /**
     * 실제로 보내지 않고 응답만 받아 보는 모드.
     *
     * 운영에서 켜지면 문자가 나가지 않는데 성공으로 보인다. 그래서 기본값은
     * 꺼짐이고, 켤 일이 있으면 환경변수로만 켠다.
     */
    private boolean testMode = false;

    private final Bank bank = new Bank();

    /** 입금받을 계좌. 셋 중 하나라도 비면 안내를 만들 수 없다. */
    @Getter
    @Setter
    public static class Bank {
        private String name = "";
        private String account = "";
        private String holder = "";

        public boolean isConfigured() {
            return notBlank(name) && notBlank(account) && notBlank(holder);
        }
    }

    /**
     * 보낼 수 있는 상태인지.
     *
     * 로컬과 테스트에서는 값이 없는 것이 정상이다. 없다고 접수를 막으면
     * 문자 설정이 접수의 전제가 된다 — 접수는 이미 끝난 뒤에 이 길이 열린다.
     */
    public boolean isConfigured() {
        return notBlank(apiKey) && notBlank(userId) && notBlank(sender) && bank.isConfigured();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
