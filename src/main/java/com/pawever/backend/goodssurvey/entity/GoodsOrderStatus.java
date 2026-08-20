package com.pawever.backend.goodssurvey.entity;

import java.util.Set;

/**
 * 굿즈 주문의 상태.
 *
 * 신청 → 결제 → 제작 → 발송으로 이어지는 한 줄기이고, 결제가 안 되거나 취소되면
 * 옆으로 빠진다. 제작팀에게는 결제가 확인된 주문만 보인다. 돈을 받지 않은 주문이
 * 제작 대기열에 섞이면 만들지 않아도 될 것을 만든다.
 */
public enum GoodsOrderStatus {

    /** 신청 정보를 저장하고 결제를 기다린다. 30분 지나면 만료된다. */
    PAYMENT_PENDING("결제 대기", false),

    /** 서버가 결제 승인 결과를 확인했다. 여기부터 제작팀에 보인다. */
    PAYMENT_COMPLETED("결제 완료", true),

    IN_PRODUCTION("제작 중", true),

    SHIPPED("발송 완료", true),

    /** 30분 안에 결제되지 않았다. 계약이 성립하지 않아 사진까지 파기한다. */
    PAYMENT_EXPIRED("결제 만료", false),

    PAYMENT_FAILED("결제 실패", false),

    /** 결제 취소까지 성공했다. 취소 API 가 성공해야만 여기로 온다. */
    CANCELED("주문 취소", false),

    /** 결제 취소가 실패했다. 사람이 확인해야 하므로 알림톡을 보내지 않는다. */
    CANCEL_FAILED("취소 처리 실패", false),

    /**
     * 1차 무료 체험단.
     *
     * 결제라는 것이 없던 때의 신청이라 위 흐름 어디에도 맞지 않는다. 결제 완료로
     * 두면 받지도 않은 돈을 받은 것으로 세고, 대기로 두면 만료 대상이 된다.
     * 관리자 목록에서 따로 걸러 보도록 이름을 나눠 둔다.
     */
    LEGACY_FREE("1차 체험단", false);

    private static final Set<GoodsOrderStatus> CANCELABLE =
            Set.of(PAYMENT_COMPLETED, IN_PRODUCTION);

    private final String label;
    private final boolean visibleToProduction;

    GoodsOrderStatus(String label, boolean visibleToProduction) {
        this.label = label;
        this.visibleToProduction = visibleToProduction;
    }

    public String label() {
        return label;
    }

    /** 제작팀에게 보이는 상태인지. */
    public boolean isVisibleToProduction() {
        return visibleToProduction;
    }

    /**
     * 관리자가 취소할 수 있는 상태인지.
     *
     * 제작 중에도 취소할 수 있다. 사진 품질 미달이나 제작 불가는 손을 대 봐야
     * 드러나는 사유라, 착수 뒤에 막아 두면 운영이 막힌다.
     */
    public boolean isCancelable() {
        return CANCELABLE.contains(this);
    }
}
