package com.pawever.backend.goodssurvey.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

@Getter
@Setter
@Component
/*
 * ignoreUnknownFields = false: yaml 에 붙지 않는 키가 있으면 기동을 멈춘다.
 * 키 이름을 잘못 적어도 스프링은 조용히 기본값을 쓰고 앱은 그대로 뜬다.
 * 관리자 서명 키가 그렇게 비어 있는 채로 배포된 적이 있다.
 */
@ConfigurationProperties(prefix = "survey.goods", ignoreUnknownFields = false)
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
    // 유료 판매의 계약·결제·공급 기록 보존 기간. 전자상거래법이 5년을 요구한다.
    // 사진과 상세주소는 계약 기록이 아니라 personalDataRetentionDays 로 먼저 지운다.
    private int contractRetentionDays = 1825;
    // 굿즈 정상가. 2차 주력은 3D 전신 피규어 한 종이라 값이 하나다.
    private int listPriceKrw = 29_900;
    // 설문에 답하고 온 사람에게 깎아 주는 금액.
    private int surveyDiscountKrw = 5_000;
    // 그 할인의 이름. 관리자 화면과 주문 기록에 그대로 남는다.
    private String surveyPromotionName = "설문 참여 할인";
    // 주문을 만든 뒤 결제를 기다리는 시간.
    private int paymentWindowMinutes = 30;
    // 광고성 정보 수신 동의 문구의 판. 문구를 고치면 이 값도 올린다.
    private String marketingConsentVersion = "marketing-v1";
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
