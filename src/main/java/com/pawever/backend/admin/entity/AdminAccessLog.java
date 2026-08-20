package com.pawever.backend.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 담당자가 고객 정보에 손댄 기록.
 *
 * 사진을 내려받거나 주소 전체를 열어 본 일은 화면에 흔적이 남지 않는다.
 * 남기지 않으면 정보가 밖으로 나갔을 때 누구를 거쳐 나갔는지 알 방법이 없다.
 *
 * 무엇을 봤는지는 남기되 본 내용은 남기지 않는다. 이력이 또 하나의
 * 개인정보 보관처가 되면 안 된다.
 */
@Entity
@Table(name = "admin_access_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long adminAccountId;

    /** PHOTO_DOWNLOAD, ADDRESS_VIEW 처럼 무엇을 했는지. */
    @Column(nullable = false, length = 40)
    private String action;

    /** 어느 주문에 대한 것인지. 주문번호로 남겨 사람이 바로 읽을 수 있게 한다. */
    @Column(nullable = false, length = 20)
    private String orderNumber;

    @Column(nullable = false)
    private Instant accessedAt;

    public static AdminAccessLog of(
            Long adminAccountId,
            String action,
            String orderNumber,
            Instant accessedAt
    ) {
        AdminAccessLog log = new AdminAccessLog();
        log.adminAccountId = adminAccountId;
        log.action = action;
        log.orderNumber = orderNumber;
        log.accessedAt = accessedAt;
        return log;
    }
}
