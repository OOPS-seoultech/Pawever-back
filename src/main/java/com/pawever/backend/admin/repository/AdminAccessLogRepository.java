package com.pawever.backend.admin.repository;

import com.pawever.backend.admin.entity.AdminAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.time.Instant;
import java.util.List;

public interface AdminAccessLogRepository extends JpaRepository<AdminAccessLog, Long> {

    List<AdminAccessLog> findByOrderNumberOrderByAccessedAtDesc(String orderNumber);

    /**
     * 보관 기간이 지난 접속기록을 지운다.
     *
     * 한 건씩 꺼내 지우지 않는다. 이력은 주문보다 훨씬 빨리 쌓여서, 한 해치를
     * 메모리로 올리면 그때 서버가 흔들린다.
     */
    @Modifying
    int deleteByAccessedAtLessThan(Instant threshold);
}
