package com.pawever.backend.goodssurvey.repository;

import com.pawever.backend.goodssurvey.entity.GoodsOrderSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GoodsOrderSequenceRepository extends JpaRepository<GoodsOrderSequence, Integer> {

    /**
     * 채번용으로 행을 잠그고 읽는다.
     *
     * 잠그지 않으면 동시에 신청한 두 사람이 같은 번호를 받는다. 주문번호에는
     * UNIQUE 가 걸려 있어 한쪽은 저장에 실패한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sequence from GoodsOrderSequence sequence where sequence.sequenceYear = :year")
    Optional<GoodsOrderSequence> findByYearForUpdate(@Param("year") int year);
}
