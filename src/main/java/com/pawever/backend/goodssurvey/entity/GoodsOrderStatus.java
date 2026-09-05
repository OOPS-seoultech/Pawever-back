package com.pawever.backend.goodssurvey.entity;

import java.util.Map;
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

    /**
     * 현장에서 직접 건넸다. 현장 수령(PICKUP) 주문의 끝이다.
     *
     * 발송 완료는 송장을 넣어야 넘어가는데, 현장 수령에는 택배사도 송장번호도
     * 없다. 이 상태가 없으면 플리마켓 주문은 제작 중에 영원히 남거나 가짜
     * 송장을 넣어 발송 완료로 꾸미게 된다. 제작팀에게 보인다 — 끝난 것을
     * 걸러 봐야 아직 만들 것이 보인다.
     */
    PICKED_UP("수령 완료", true),

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
     *
     * 제작팀에게는 보인다. 돈은 받지 않았지만 만들어 보내야 하는 물건이다.
     * 안 보이게 두면 100건을 제작 화면에서 찾을 방법이 없다.
     */
    LEGACY_FREE("1차 체험단", true);

    private static final Set<GoodsOrderStatus> CANCELABLE =
            Set.of(PAYMENT_COMPLETED, IN_PRODUCTION);

    /**
     * 선착순 자리를 놓는 상태.
     *
     * 계약이 성립하지 않았거나 되돌려진 건이다. 이 셋은 정원을 세는 자리에서
     * 빠져야 한다 — 결제되지 않아 사라진 주문이 자리를 잡고 있으면 100명
     * 모집이 실제로는 99명이 되고, 쌓이면 아무도 신청하지 못한 채 닫힌다.
     *
     * CANCEL_FAILED 는 여기 없다. 돈은 받아 두고 환불에 실패한 상태라 사람이
     * 정리하기 전까지 그 물건은 이 사람 몫이다. 자리를 놓으면 환불도 못 한 채
     * 같은 자리를 다른 사람에게 판다.
     *
     * PAYMENT_PENDING 도 여기 없다. 기다리는 동안 자리를 놓으면, 안내받은
     * 계좌로 입금하려던 사람이 낼 곳을 잃는다.
     */
    private static final Set<GoodsOrderStatus> RELEASES_SLOT =
            Set.of(PAYMENT_EXPIRED, PAYMENT_FAILED, CANCELED);

    /** 선착순 자리를 놓는 상태들. 정원을 세는 쪽에서 제외 목록으로 쓴다. */
    public static Set<GoodsOrderStatus> releasesSlot() {
        return RELEASES_SLOT;
    }

    /**
     * 사람이 손으로 옮길 수 있는 길.
     *
     * 여기 없는 길은 버튼이 아니라 사건으로만 간다 — 발송 완료는 송장 등록,
     * 수령 완료는 현장 수령 버튼, 취소는 취소 절차. 그 사건이 함께 남겨야
     * 하는 것(송장, 파기 기준일, 환불 확인)을 건너뛰지 못하게 하기 위해서다.
     *
     * 결제 대기에서 제작 중으로는 못 간다. 돈을 받지 않은 피규어가 제작
     * 대기열에 들어간다. 결제 완료·제작 중에서 만료·실패로도 못 간다. 둘은
     * 자리를 놓는 상태라, 돈은 받아 둔 채 같은 자리를 다른 사람에게 팔게 된다.
     * 제작 중에서 결제 완료로는 한 단계 되돌릴 수 있다 — 잘못 누른 것을
     * 고치는 길이다.
     *
     * 끝난 상태(발송·수령·취소·만료·실패)에서는 어디로도 못 간다. 만료는
     * 사진이 이미 파기된 뒤라 되살려도 만들 수 없다. 늦게 입금한 사람은
     * 새로 신청해야 한다.
     *
     * 1차 체험단은 결제가 없던 주문이라 제작 중으로만 간다. 결제 완료로
     * 바꾸면 받지도 않은 돈이 매출로 잡히고, 만료·실패로 바꾸면 없던 결제가
     * 실패한 것이 된다.
     *
     * 관리자 화면(adminOrderStatus.ts)이 같은 표를 들고 있다. 여기를 고치면
     * 그쪽도 함께 고친다.
     */
    private static final Map<GoodsOrderStatus, Set<GoodsOrderStatus>> MANUAL_TRANSITIONS = Map.of(
            PAYMENT_PENDING, Set.of(PAYMENT_COMPLETED, PAYMENT_EXPIRED, PAYMENT_FAILED),
            PAYMENT_COMPLETED, Set.of(IN_PRODUCTION),
            IN_PRODUCTION, Set.of(PAYMENT_COMPLETED),
            LEGACY_FREE, Set.of(IN_PRODUCTION)
    );

    /** 이 상태에서 사람이 손으로 {@code next} 로 옮길 수 있는지. */
    public boolean canManuallyBecome(GoodsOrderStatus next) {
        return MANUAL_TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
    }

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
