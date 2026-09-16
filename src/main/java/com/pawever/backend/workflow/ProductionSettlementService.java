package com.pawever.backend.workflow;

import java.time.Clock;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 제작비 정산 대상 여부를 실제 업무 완료 시점에 평가한다.
 *
 * <p>품질 검수는 아직 포장·접수가 남은 중간 단계다. 택배는 우체국이 실제로
 * 접수했을 때, 직접 수령은 OWNER가 포장을 끝냈을 때만 주문당 한 번 기록한다.
 */
@Service
@RequiredArgsConstructor
public class ProductionSettlementService {
  private final ProductionCompensationSettingsRepository settings;
  private final ProductionPaidWorkerRepository paidWorkers;
  private final ProductionSettlementRepository settlements;
  private final FinishingRecordRepository finishingRecords;
  private final ProductionTaskRepository tasks;
  private final Clock clock;

  public String recordAtFulfillment(String orderNumber) {
    if (settlements.existsByOrderNumber(orderNumber)) return "ALREADY_RECORDED";
    if (!settings.findById(1L).map(ProductionCompensationSettings::isEnabled).orElse(false))
      return "DISABLED";

    var passed =
        finishingRecords.findByOrderNumberOrderByIdAsc(orderNumber).stream()
            .filter(r -> "QC".equals(r.getStage()) && "PASSED".equals(r.getDecision()))
            .max(Comparator.comparing(FinishingRecord::getId))
            .orElse(null);
    if (passed == null) return "REVIEW_REQUIRED";

    var task = tasks.findById(passed.getTaskId()).orElse(null);
    if (task == null || task.getAssigneeId() == null) return "REVIEW_REQUIRED";
    if (!paidWorkers.existsById(task.getAssigneeId())) return "UNPAID_WORKER";

    settlements.saveAndFlush(
        ProductionSettlement.of(task, task.getAssigneeId(), clock.instant()));
    return "RECORDED";
  }
}
