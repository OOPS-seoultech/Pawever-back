package com.pawever.backend.goodssurvey.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "survey.goods")
public class GoodsSurveyProperties {

    private String campaignId = "goods-2026-07";
    private int reservationMinutes = 15;
    private int uploadUrlMinutes = 10;
    private int personalDataRetentionDays = 90;
    // 2차 안내용 이메일 보유 기간. 화면에 "1년"으로 고지하고 받는다.
    private int noticeRetentionDays = 365;
    // 설문 응답과 사연의 보유 기간. 방침에 "수집 후 2년"으로 고지한다.
    // 공개 동의를 받은 사진도 이 기간을 따른다. 제작용 사진은 배송 뒤
    // personalDataRetentionDays 로 훨씬 먼저 지운다.
    private int surveyRetentionDays = 730;
    // 파기 작업이 도는 시각. 접속이 가장 적은 새벽에 한 번 돈다.
    private String purgeCron = "0 15 4 * * *";
    private String questionnaireVersion = "2026-07-25-v2";
    private Set<String> legacyQuestionnaireVersions = Set.of("2026-07-23-v1");
    private String privacyConsentVersion = "2026-07-23";
    private String photoBucket;
    // 설문 결과 내보내기 토큰. 비어 있으면 내보내기 자체가 막힌다.
    // 개인정보가 그대로 나가는 통로라 설정되지 않은 상태에서 열려선 안 된다.
    private String exportToken = "";
}
