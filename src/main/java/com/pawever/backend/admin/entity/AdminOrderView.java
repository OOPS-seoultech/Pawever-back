package com.pawever.backend.admin.entity;

import com.pawever.backend.goodssurvey.entity.GoodsOrderStatus;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * 관리자 목록을 일 단위로 나눈 자리.
 *
 * 상태 아홉 개를 칩으로 늘어놓으면 기본이 "전체"가 되어, 8월에 들어온 100건이
 * 오늘 들어온 세 건을 덮는다. 실제로 하는 일은 넷뿐이고 일마다 보는 값도
 * 누르는 것도 다르다 — 통장을 대조하는 일, 만들기 시작하는 일, 보내는 일,
 * 어그러진 것을 정리하는 일.
 *
 * 그래서 상태가 아니라 일로 묶는다. 결제 완료와 1차 체험단은 돈을 받은 방식이
 * 다르지만 다음에 할 일은 같아서 한 자리에 둔다.
 *
 * 뷰마다 주로 하는 일이 하나씩이라, 묶음 처리도 그 뷰 안에서 자연스럽게
 * 끝난다. 100건을 다섯 페이지에 걸쳐 고르던 것이 이 구조에서는 한 번이다.
 *
 * 모든 상태가 어느 한 뷰에는 들어가야 한다. 빠진 상태가 생기면 그 주문은
 * 화면 어디에서도 보이지 않는다.
 */
public enum AdminOrderView {

    /** 통장을 보고 입금을 확인하는 자리. */
    PAYMENT_CHECK("입금 확인", GoodsOrderStatus.PAYMENT_PENDING),

    /** 돈은 들어왔고 아직 안 만든 것. 여기서 묶어 제작을 시작한다. */
    PRODUCTION_QUEUE(
            "제작 대기",
            GoodsOrderStatus.PAYMENT_COMPLETED,
            GoodsOrderStatus.LEGACY_FREE),

    /** 만드는 중. 다 만들면 송장을 넣거나 현장에서 건넨다. */
    IN_PRODUCTION("제작 중", GoodsOrderStatus.IN_PRODUCTION),

    /** 손을 뗀 것. 조회만 한다. */
    DONE("완료", GoodsOrderStatus.SHIPPED, GoodsOrderStatus.PICKED_UP),

    /** 어그러진 것. 사람이 들여다봐야 한다. */
    PROBLEM(
            "문제",
            GoodsOrderStatus.PAYMENT_EXPIRED,
            GoodsOrderStatus.PAYMENT_FAILED,
            GoodsOrderStatus.CANCELED,
            GoodsOrderStatus.CANCEL_FAILED);

    private final String label;
    private final Set<GoodsOrderStatus> statuses;

    AdminOrderView(String label, GoodsOrderStatus... statuses) {
        this.label = label;
        this.statuses = EnumSet.copyOf(Arrays.asList(statuses));
    }

    public String label() {
        return label;
    }

    public Set<GoodsOrderStatus> statuses() {
        return statuses;
    }
}
