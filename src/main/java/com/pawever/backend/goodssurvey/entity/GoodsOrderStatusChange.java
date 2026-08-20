package com.pawever.backend.goodssurvey.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 주문 상태가 바뀐 기록.
 *
 * 주문 행에는 지금 상태만 남는다. 언제 누가 왜 바꿨는지는 여기에 쌓는다.
 * 취소나 환불로 다투게 되면 지금 상태만으로는 아무것도 설명할 수 없다.
 *
 * 자동으로 바뀐 것(결제 승인, 30분 만료)도 남긴다. 사람이 한 것과 시스템이 한
 * 것을 나누려고 담당자 자리를 비워 둔다.
 */
@Entity
@Table(name = "goods_order_status_changes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoodsOrderStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 어느 주문의 기록인지. 주문번호가 아니라 응답 식별자로 잇는다. */
    @Column(nullable = false, length = 36)
    private String responseId;

    /** 처음 만들어진 주문은 이전 상태가 없다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private GoodsOrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GoodsOrderStatus toStatus;

    @Column(nullable = false)
    private Instant changedAt;

    /** 사람이 바꿨으면 그 계정, 시스템이 바꿨으면 비어 있다. */
    @Column(length = 100)
    private String changedBy;

    @Column(length = 300)
    private String memo;

    public static GoodsOrderStatusChange of(
            String responseId,
            GoodsOrderStatus fromStatus,
            GoodsOrderStatus toStatus,
            Instant changedAt,
            String changedBy,
            String memo
    ) {
        GoodsOrderStatusChange change = new GoodsOrderStatusChange();
        change.responseId = responseId;
        change.fromStatus = fromStatus;
        change.toStatus = toStatus;
        change.changedAt = changedAt;
        change.changedBy = changedBy;
        change.memo = memo;
        return change;
    }

    /** 시스템이 바꾼 기록. 결제 승인과 30분 만료가 여기로 온다. */
    public static GoodsOrderStatusChange bySystem(
            String responseId,
            GoodsOrderStatus fromStatus,
            GoodsOrderStatus toStatus,
            Instant changedAt,
            String memo
    ) {
        return of(responseId, fromStatus, toStatus, changedAt, null, memo);
    }
}
