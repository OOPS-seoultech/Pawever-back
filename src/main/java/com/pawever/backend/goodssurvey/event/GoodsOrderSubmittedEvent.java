package com.pawever.backend.goodssurvey.event;

/**
 * 굿즈 신청이 접수되어 커밋된 뒤에 나가는 알림거리.
 *
 * 엔티티를 그대로 넘기지 않는다. 알림은 트랜잭션이 끝난 뒤 다른 스레드에서
 * 처리되므로, 영속성 컨텍스트가 닫힌 엔티티를 들고 가면 지연 로딩에서 터진다.
 * 보낼 값만 여기에 복사해 둔다.
 *
 * 이 기록에는 이름과 연락처가 들어 있다. 대표님 판단으로 알림에 그대로
 * 싣기로 했고, 그래서 개인정보처리방침 제6조에 텔레그램 국외 처리를 적어
 * 두었다. 실을 항목을 늘리려면 방침도 같이 봐야 한다.
 */
public record GoodsOrderSubmittedEvent(
        String orderNumber,
        String guardianName,
        String phone,
        String petName,
        String goodsLabel,
        int paymentAmountKrw,
        boolean surveyParticipant,
        String trafficSource
) {
}
