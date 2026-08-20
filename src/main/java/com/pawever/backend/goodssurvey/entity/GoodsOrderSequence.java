package com.pawever.backend.goodssurvey.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 연도별 주문번호 채번기.
 *
 * PE-2026-000001 처럼 연도 안에서 1부터 센다. 기존 주문에서 최대값을 찾아 1을
 * 더하는 방식은 두 사람이 동시에 신청하면 같은 번호를 받는다. 행 하나를 잠그고
 * 올리면 순서가 강제된다.
 */
@Entity
@Table(name = "goods_order_sequences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoodsOrderSequence {

    /** 채번 연도. 해가 바뀌면 새 행이 생기고 번호는 다시 1부터 시작한다. */
    @Id
    @Column(name = "sequence_year")
    private int sequenceYear;

    @Column(nullable = false)
    private int lastNumber;

    public static GoodsOrderSequence startOf(int year) {
        GoodsOrderSequence sequence = new GoodsOrderSequence();
        sequence.sequenceYear = year;
        sequence.lastNumber = 0;
        return sequence;
    }

    /** 다음 번호를 하나 꺼낸다. 부른 쪽이 행을 잠그고 있어야 한다. */
    public String issue() {
        lastNumber++;
        return "PE-%d-%06d".formatted(sequenceYear, lastNumber);
    }
}
