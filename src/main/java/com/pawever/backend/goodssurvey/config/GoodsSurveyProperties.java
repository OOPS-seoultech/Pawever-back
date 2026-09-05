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
    /*
     * 11차 회의록에서 정한 2차 가격.
     *   설문을 거치지 않고 바로 신청 = 29,900 (2차 공개 사전판매가)
     *   설문 참여자                  = 23,900
     * 여기 기본값은 yaml 기본값과 같아야 한다. 다르면 시험은 통과하는데
     * 실제로는 다른 금액이 청구된다.
     */
    private int listPriceKrw = 29_900;
    private int surveyDiscountKrw = 6_000;

    /**
     * 플리마켓 모집.
     *
     * 비어 있으면 이 통로는 없는 것으로 본다. 행사가 끝나면 값을 지워 닫는다 —
     * 배포 없이 환경변수만 비우면 되고, 그러면 새 주문이 들어올 길 자체가
     * 사라진다. 이미 들어온 주문은 모집에 붙은 통로를 보고 계산하므로 값이
     * 바뀌지 않는다.
     */
    private String fleaCampaignId = "";

    /*
     * 현장 한정 할인. 정가 29,900 에서 18,000 을 빼 11,900 이 된다.
     * 디자인이 적어 둔 "60.2% 할인"이 이 값이다(18,000 / 29,900 = 60.2%).
     *
     * 설문 참여 할인과 겹쳐 쓰지 않는다. 플리마켓은 설문을 거치지 않는 자리라
     * 누가 오든 같은 값이다.
     */
    private int fleaDiscountKrw = 18_000;
    private String fleaPromotionName = "과기대 플리마켓 할인";

    /**
     * 배송비.
     *
     * 주문을 만들 때 한 번 읽어 주문에 적는다. 나중에 값이 바뀌어도 이미 받은
     * 주문의 금액은 그대로여야 한다.
     */
    private int shippingFeeKrw = 3_000;
    // 그 할인의 이름. 관리자 화면과 주문 기록에 그대로 남는다.
    private String surveyPromotionName = "설문 참여 할인";
    // 주문을 만든 뒤 결제를 기다리는 시간.
    private int paymentWindowMinutes = 30;

    /**
     * 플리마켓 주문이 입금을 기다리는 시간.
     *
     * 48시간은 택배로 받는 상시 판매의 기한이다. 은행 앱을 열 시간, 주말,
     * 점검 시간까지 넉넉히 잡은 값이다.
     *
     * 현장은 다르다. QR 을 찍고 그 자리에서 내는 자리라, 내지 않을 사람이
     * 이틀씩 70자리 중 하나를 잡고 있으면 실제로 낼 사람이 마감 화면을 본다.
     * 만료된 자리는 스스로 돌아오지만 이틀 뒤에 돌아오는 자리는 행사가
     * 끝난 뒤에 열린다.
     *
     * 값은 환경변수로 바꾼다. 행사 당일 줄이 길면 배포 없이 조절해야 한다.
     */
    private int fleaPaymentWindowMinutes = 180;
    // 광고성 정보 수신 동의 문구의 판. 문구를 고치면 이 값도 올린다.
    private String marketingConsentVersion = "marketing-v1";
    /**
     * 담당자 접속기록 보관 기간.
     *
     * 개인정보처리시스템의 접속기록은 1년 이상 보관해야 한다. 요구하는 것은
     * 최소 기간이지 영원히 두라는 것이 아니라, 지우는 자리를 함께 둔다.
     */
    private int adminAccessLogRetentionDays = 365;

    // 파기 작업이 도는 시각. 접속이 가장 적은 새벽에 한 번 돈다.
    private String purgeCron = "0 15 4 * * *";

    /**
     * 결제 대기 만료를 걷어내는 주기.
     *
     * 결제 대기는 30분짜리다. 새벽 파기만 믿으면 하루를 기다린다.
     */
    private String paymentExpiryCron = "0 */5 * * * *";
    private String questionnaireVersion = "2026-07-25-v2";
    private Set<String> legacyQuestionnaireVersions = Set.of("2026-07-23-v1");
    private String privacyConsentVersion = "2026-07-23";
    private String photoBucket;
    // 설문 결과 내보내기 토큰. 비어 있으면 내보내기 자체가 막힌다.
    // 개인정보가 그대로 나가는 통로라 설정되지 않은 상태에서 열려선 안 된다.
    private String exportToken = "";
}
