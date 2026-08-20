package com.pawever.backend.goodssurvey.repository;

import com.pawever.backend.goodssurvey.entity.GoodsOrderStatusChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoodsOrderStatusChangeRepository extends JpaRepository<GoodsOrderStatusChange, Long> {

    /** 주문 상세에서 시간 순으로 보여준다. */
    List<GoodsOrderStatusChange> findByResponseIdOrderByChangedAtAsc(String responseId);

    /** 주문이 파기될 때 이력도 함께 지운다. */
    void deleteByResponseId(String responseId);
}
