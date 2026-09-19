package com.pawever.backend.workflow;

import com.pawever.backend.admin.entity.AdminRole;
import com.pawever.backend.admin.repository.AdminAccountRepository;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 실제 송금은 자동화하지 않고, 선택한 적립 항목과 이체 기록만 불변으로 남긴다. */
@Service
@RequiredArgsConstructor
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class ProductionPayoutService {
  private final WorkflowService workflow;
  private final StaffPermissions access;
  private final PrintBatchService plates;
  private final ProductionSettlementRepository settlements;
  private final ProductionPayoutBatchRepository batches;
  private final ProductionPayoutItemRepository items;
  private final AdminAccountRepository accounts;
  private final Clock clock;

  public Map<String, Object> summary() {
    var actor = access.current();
    boolean owner = actor.getRole() == AdminRole.OWNER;
    var visible =
        settlements.findAllByOrderByIdDesc().stream()
            .filter(s -> owner || Objects.equals(s.getWorkerId(), actor.getId()))
            .toList();
    var rows = visible.stream().map(this::settlementView).toList();
    var totals = new LinkedHashMap<String, Integer>();
    for (var settlement : visible)
      totals.merge(settlement.getPaymentStatus(), settlement.getAmountKrw(), Integer::sum);
    var visibleBatches =
        batches.findAllByOrderByIdDesc().stream()
            .filter(b -> owner || Objects.equals(b.getBeneficiaryId(), actor.getId()))
            .map(this::batchView)
            .toList();
    return plates.map(
        "isOwner", owner,
        "settlements", rows,
        "totalsKrw", totals,
        "payoutBatches", visibleBatches);
  }

  public Map<String, Object> prepare(String key, Map<String, Object> input) {
    owner();
    return workflow.command(
        "production-payout-batches",
        key,
        input,
        () -> {
          var actor = owner();
          var selectedIds = settlementIds(input);
          var selected = settlements.findByIdIn(selectedIds);
          if (selected.size() != selectedIds.size()) throw plates.bad("존재하지 않는 정산 항목이 있습니다.");
          Long beneficiary = selected.get(0).getWorkerId();
          if (selected.stream().anyMatch(s -> !Objects.equals(beneficiary, s.getWorkerId())))
            throw plates.bad("한 지급 묶음에는 한 명의 담당자 정산만 선택할 수 있습니다.");
          if (selected.stream().anyMatch(s -> !"UNPAID".equals(s.getPaymentStatus())))
            throw new WorkflowException(409, "SETTLEMENT_ALREADY_SELECTED", "이미 지급 준비 또는 완료된 정산 항목이 포함되어 있습니다.");
          if (selected.stream().anyMatch(s -> items.existsBySettlementId(s.getId())))
            throw new WorkflowException(409, "SETTLEMENT_ALREADY_SELECTED", "다른 지급 묶음에 이미 선택된 정산 항목이 있습니다.");
          int gross = selected.stream().mapToInt(ProductionSettlement::getAmountKrw).sum();
          long deduction = plates.number(input, "deductionKrw");
          if (deduction > gross) throw plates.bad("공제액은 세전 합계보다 클 수 없습니다.");
          var batch =
              batches.saveAndFlush(
                  ProductionPayoutBatch.prepare(
                      beneficiary, gross, (int) deduction, actor.getId(), clock.instant()));
          for (var settlement : selected) settlement.preparePayout();
          settlements.saveAllAndFlush(selected);
          items.saveAllAndFlush(selected.stream().map(s -> ProductionPayoutItem.of(batch.getId(), s)).toList());
          workflow.audit(
              "production-payout-batch:" + batch.getId(),
              "PREPARE_PAYOUT",
              null,
              "settlements=" + selectedIds + ",gross=" + gross + ",deduction=" + deduction,
              null);
          return batchView(batch);
        });
  }

  public Map<String, Object> markPaid(Long batchId, String key, Map<String, Object> input) {
    owner();
    return workflow.command(
        "production-payout-batch:" + batchId,
        key,
        input,
        () -> {
          var actor = owner();
          String reference = plates.text(input, "reference", 300);
          if (reference.isBlank()) throw plates.bad("은행 이체 확인 번호 또는 증빙 메모를 입력해 주세요.");
          var batch =
              batches
                  .findById(batchId)
                  .orElseThrow(() -> new WorkflowException(404, "NOT_FOUND", "지급 묶음을 찾을 수 없습니다."));
          var selected =
              settlements.findByIdIn(
                  items.findByPayoutBatchIdOrderByIdAsc(batchId).stream()
                      .map(ProductionPayoutItem::getSettlementId)
                      .toList());
          if (selected.isEmpty()) throw new WorkflowException(409, "PAYOUT_EMPTY", "지급 항목이 없는 묶음입니다.");
          batch.markPaid(actor.getId(), reference, clock.instant());
          selected.forEach(ProductionSettlement::markPaid);
          settlements.saveAllAndFlush(selected);
          batches.saveAndFlush(batch);
          workflow.audit(
              "production-payout-batch:" + batchId,
              "MARK_PAYOUT_PAID",
              "PREPARED",
              "PAID reference=" + reference,
              null);
          return batchView(batch);
        });
  }

  private Set<Long> settlementIds(Map<String, Object> input) {
    if (!(input.get("settlementIds") instanceof List<?> raw) || raw.isEmpty() || raw.size() > 100)
      throw plates.bad("선택할 정산 항목을 1~100건으로 정해 주세요.");
    var ids = new TreeSet<Long>();
    for (var value : raw) {
      if (!(value instanceof Number n) || n.longValue() < 1 || n.doubleValue() != n.longValue())
        throw plates.bad("정산 항목을 확인해 주세요.");
      if (!ids.add(n.longValue())) throw plates.bad("같은 정산 항목을 두 번 선택할 수 없습니다.");
    }
    return ids;
  }

  private com.pawever.backend.admin.entity.AdminAccount owner() {
    var actor = access.current();
    if (actor.getRole() != AdminRole.OWNER)
      throw new WorkflowException(403, "FORBIDDEN", "실제 지급 준비와 이체 완료 기록은 OWNER만 할 수 있습니다.");
    return actor;
  }

  private Map<String, Object> settlementView(ProductionSettlement s) {
    return plates.map(
        "id", s.getId(),
        "orderNumber", s.getOrderNumber(),
        "workerId", s.getWorkerId(),
        "workerName", accounts.findById(s.getWorkerId()).map(a -> a.getName()).orElse("이전 담당자"),
        "amountKrw", s.getAmountKrw(),
        "paymentStatus", s.getPaymentStatus(),
        "createdAt", s.getCreatedAt().toString());
  }

  private Map<String, Object> batchView(ProductionPayoutBatch b) {
    return plates.map(
        "id", b.getId(),
        "beneficiaryId", b.getBeneficiaryId(),
        "beneficiaryName", accounts.findById(b.getBeneficiaryId()).map(a -> a.getName()).orElse("이전 담당자"),
        "grossKrw", b.getGrossKrw(),
        "deductionKrw", b.getDeductionKrw(),
        "netKrw", b.getNetKrw(),
        "status", b.getStatus(),
        "preparedAt", b.getPreparedAt().toString(),
        "paidAt", b.getPaidAt() == null ? null : b.getPaidAt().toString(),
        "transferReference", b.getTransferReference(),
        "settlementIds", items.findByPayoutBatchIdOrderByIdAsc(b.getId()).stream().map(ProductionPayoutItem::getSettlementId).toList());
  }
}
