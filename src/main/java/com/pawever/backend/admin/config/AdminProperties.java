package com.pawever.backend.admin.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "admin")
public class AdminProperties {

    /**
     * 관리자 토큰 서명 키.
     *
     * 앱 회원 토큰과 나눈다. 같은 키를 쓰면 앱 토큰 하나로 관리자 요청을
     * 통과시킬 수 있는지가 claim 검사 하나에만 달린다.
     *
     * 기본값을 두지 않는다. 두면 그 값이 그대로 운영에 나간다.
     * 비어 있으면 관리자 로그인이 열리지 않는다.
     */
    private String jwtSecret = "";

    /** 관리자 토큰 유효 시간. 고객 정보를 다루는 화면이라 짧게 둔다. */
    private long jwtExpirationMillis = 8 * 60 * 60 * 1000L;

    /**
     * 첫 관리자를 세울 때만 쓰는 값.
     *
     * 초대할 사람이 아직 없어 생기는 닭과 달걀을 푸는 통로다. 관리자가 하나라도
     * 생기면 그 통로는 스스로 닫힌다.
     */
    private String bootstrapToken = "";
}
