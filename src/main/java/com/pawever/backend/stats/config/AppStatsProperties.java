package com.pawever.backend.stats.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "stats")
public class AppStatsProperties {

    /**
     * 앱 통계 CSV를 내려받는 토큰. 비어 있으면 내보내기가 열리지 않는다.
     *
     * 설문 내보내기 토큰과 일부러 나눠 둔다. 통계는 집계값뿐이라 팀 안에서
     * 넓게 공유되는데, 같은 토큰을 쓰면 이름·연락처·주소가 나가는 설문 통로까지
     * 함께 열리게 된다.
     */
    private String exportToken = "";
}
