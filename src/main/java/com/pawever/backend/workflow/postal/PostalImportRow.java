package com.pawever.backend.workflow.postal;

import com.pawever.backend.global.common.BaseTimeEntity;
import com.pawever.backend.global.common.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 붙여넣은 우체국 내역의 한 줄.
 *
 * <p>원문과 판단 결과를 함께 남긴다. 나중에 "왜 이 주문에 이 송장이 붙었나"를
 * 물었을 때, 무엇을 보고 그렇게 정했는지 되짚을 수 있어야 한다.
 *
 * <p>원문과 이름 표기에는 고객 이름이 들어 있어 주문 줄과 같은 방식으로 암호화해
 * 저장한다.
 */
@Entity
@Table(name = "postal_import_rows")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostalImportRow extends BaseTimeEntity {

    /** 사람이 고르는 동안 다른 곳에서 바뀌면 덮어쓰지 않는다. */
    @Version private long version;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long batchId;

    /** 붙여넣은 원문에서 몇 번째 줄이었는지. 사람이 원문과 대조할 때 쓴다. */
    @Column(nullable = false)
    private int lineNumber;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 1000)
    private String rawLine;

    /** 13자리. 앞자리 0을 지키려고 문자열로 둔다. */
    @Column(length = 20)
    private String trackingNumber;

    @Column private Integer postageKrw;

    /** 5자리. 같은 이유로 문자열이다. */
    @Column(length = 10)
    private String postalCode;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 1000)
    private String recipientLabel;

    /**
     * 이 줄을 어떻게 다룰지.
     *
     * <p>AUTO_MATCH(자동 연결 후보) / NEEDS_REVIEW(사람이 골라야 함) /
     * CONFIRMED_MANUAL(사람이 고름) / BLOCKED(막힘) / ALREADY_APPLIED(이미 반영됨) /
     * COMMITTED(반영 완료)
     */
    @Column(nullable = false, length = 20)
    private String status;

    /** 왜 그렇게 판단했는지. AUTO / MODAL / POSTAL_CODE_MISMATCH / NO_CANDIDATE 등. */
    @Column(length = 30)
    private String matchKind;

    @Column(length = 500)
    private String reason;

    /** 사람에게 보여 줄 후보 주문번호들. 쉼표로 잇는다. */
    @Column(length = 1000)
    private String candidateOrderNumbers;

    /** 연결된 주문. 자동이든 사람이 고른 것이든 여기에 적힌다. */
    @Column(length = 20)
    private String matchedOrderNumber;

    /** 읽는 중에 발견한 문제들. 쉼표로 잇는다. */
    @Column(length = 500)
    private String issues;

    private Long resolvedBy;
    private Instant resolvedAt;

    @Column(length = 300)
    private String resolveReason;

    private Instant committedAt;

    public static PostalImportRow of(
            Long batchId,
            PostalReceipt receipt,
            String status,
            String matchKind,
            String matchedOrderNumber,
            List<String> candidates,
            String reason) {
        PostalImportRow row = new PostalImportRow();
        row.batchId = batchId;
        row.lineNumber = receipt.lineNumber();
        row.rawLine = receipt.rawLine();
        row.trackingNumber = receipt.trackingNumber();
        row.postageKrw = receipt.postageKrw();
        row.postalCode = receipt.postalCode();
        row.recipientLabel = receipt.recipientLabel();
        row.status = status;
        row.matchKind = matchKind;
        row.matchedOrderNumber = matchedOrderNumber;
        row.candidateOrderNumbers = String.join(",", candidates);
        row.issues = String.join(",", receipt.issues());
        row.reason = reason;
        return row;
    }

    /** 읽지 못한 줄도 남긴다. 조용히 버리면 몇 건이 빠졌는지 아무도 모른다. */
    public static PostalImportRow unreadable(Long batchId, int lineNumber, String rawLine, String code) {
        PostalImportRow row = new PostalImportRow();
        row.batchId = batchId;
        row.lineNumber = lineNumber;
        row.rawLine = rawLine;
        row.status = "BLOCKED";
        row.matchKind = code;
        row.candidateOrderNumbers = "";
        row.issues = code;
        row.reason = "읽을 수 없는 줄입니다. 원문을 확인해 주세요.";
        return row;
    }

    public List<String> candidates() {
        if (candidateOrderNumbers == null || candidateOrderNumbers.isBlank()) return List.of();
        return List.of(candidateOrderNumbers.split(","));
    }

    /** 사람이 후보 중 하나를 골랐다. */
    public void resolve(String orderNumber, Long actorId, Instant at, String why) {
        this.matchedOrderNumber = orderNumber;
        this.status = "CONFIRMED_MANUAL";
        this.resolvedBy = actorId;
        this.resolvedAt = at;
        this.resolveReason = why;
    }

    public void markCommitted(Instant at) {
        this.status = "COMMITTED";
        this.committedAt = at;
    }

    public void block(String why) {
        this.status = "BLOCKED";
        this.reason = why;
    }

    public void markAlreadyApplied(String why) {
        this.status = "ALREADY_APPLIED";
        this.reason = why;
    }

    /** 일괄 확정에서 고를 수 있는 줄인지. */
    public boolean isSelectable() {
        return "AUTO_MATCH".equals(status) || "CONFIRMED_MANUAL".equals(status);
    }
}
