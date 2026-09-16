package com.pawever.backend.workflow.postal;

import static com.pawever.backend.admin.entity.PermissionKey.IMPORT_SHIPMENT_RESULTS;

import com.pawever.backend.goodssurvey.entity.GoodsDeliveryMethod;
import com.pawever.backend.goodssurvey.entity.GoodsOrderStatus;
import com.pawever.backend.goodssurvey.entity.GoodsSurveyFulfillment;
import com.pawever.backend.goodssurvey.repository.GoodsSurveyFulfillmentRepository;
import com.pawever.backend.workflow.ShipmentExportItemRepository;
import com.pawever.backend.workflow.StaffPermissions;
import com.pawever.backend.workflow.ProductionSettlementService;
import com.pawever.backend.workflow.WorkflowException;
import com.pawever.backend.workflow.WorkflowService;
import com.pawever.backend.workflow.notification.ShipmentNotificationService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 우체국에서 받아 온 접수 내역을 주문과 맞춰 송장을 채운다.
 *
 * <p>세 단계다. 붙여넣어 미리 보고(preview), 애매한 줄만 사람이 고르고(resolve),
 * 고른 것을 한 번에 반영한다(commit).
 *
 * <p>미리보기는 아무것도 바꾸지 않는다. 붙여넣자마자 고객에게 뭔가 나가는 일은
 * 없다. 실제로 주문이 바뀌는 것은 commit 한 번뿐이다.
 */
@Service
@RequiredArgsConstructor
public class PostalImportService {

    private final PostalImportBatchRepository batches;
    private final PostalImportRowRepository rows;
    private final ShipmentExportItemRepository exportItems;
    private final GoodsSurveyFulfillmentRepository orders;
    private final StaffPermissions access;
    private final WorkflowService workflow;
    private final ProductionSettlementService compensation;
    private final ShipmentNotificationService notifications;
    private final Clock clock;

    /**
     * 붙여넣은 내역을 읽어 무엇이 자동이고 무엇이 애매한지 보여 준다.
     *
     * <p>주문은 건드리지 않는다. 판단 결과만 남겨 두었다가 사람이 확정할 때 쓴다.
     */
    @Transactional
    public Map<String, Object> preview(Long outboundBatchId, String text) {
        access.require(IMPORT_SHIPMENT_RESULTS);
        var pool = candidatesOf(outboundBatchId);
        if (pool.isEmpty()) {
            throw new WorkflowException(400, "EMPTY_BATCH", "이 발송 묶음에 대조할 주문이 없습니다.");
        }

        var parsed = PostalReceiptParser.parse(text);
        if (parsed.receipts().isEmpty() && parsed.errors().isEmpty()) {
            throw new WorkflowException(400, "EMPTY_INPUT", "읽을 내용이 없습니다.");
        }

        var batch =
                batches.saveAndFlush(
                        PostalImportBatch.of(
                                outboundBatchId,
                                access.current().getId(),
                                clock.instant(),
                                parsed.receipts().size()));

        List<PostalImportRow> saved = new ArrayList<>();
        for (var receipt : parsed.receipts()) {
            saved.add(rows.save(judge(batch.getId(), receipt, pool)));
        }
        for (var error : parsed.errors()) {
            saved.add(
                    rows.save(
                            PostalImportRow.unreadable(
                                    batch.getId(), error.lineNumber(), error.rawLine(), error.code())));
        }
        rows.flush();

        workflow.audit(
                "postal-import:" + batch.getId(),
                "PREVIEW_POSTAL_IMPORT",
                null,
                null,
                "발송 묶음 " + outboundBatchId + " · " + saved.size() + "행");
        return view(batch, saved, pool);
    }

    /** 한 줄을 어떻게 다룰지 정한다. */
    private PostalImportRow judge(Long batchId, PostalReceipt receipt, List<PostalNameMatcher.Candidate> pool) {
        // 읽는 중에 문제가 있었으면 맞춰 보기 전에 막는다. 송장이 겹쳐 들어온
        // 줄을 자동으로 붙이면 한쪽 주문은 영영 송장을 잃는다.
        if (receipt.hasIssues()) {
            return PostalImportRow.of(
                    batchId,
                    receipt,
                    "BLOCKED",
                    "INPUT_ISSUE",
                    null,
                    List.of(),
                    "원문에 문제가 있습니다: " + String.join(", ", receipt.issues()));
        }

        var match = PostalNameMatcher.match(receipt.recipientLabel(), receipt.postalCode(), pool);
        String status =
                switch (match.kind()) {
                    case AUTO -> "AUTO_MATCH";
                    case MODAL, POSTAL_CODE_MISMATCH -> "NEEDS_REVIEW";
                    case NO_CANDIDATE -> "BLOCKED";
                };
        return PostalImportRow.of(
                batchId,
                receipt,
                status,
                match.kind().name(),
                match.matched(),
                match.candidates(),
                match.reason());
    }

    /** 사람이 후보 중 하나를 고른다. 왜 골랐는지 함께 남긴다. */
    @Transactional
    public Map<String, Object> resolve(Long batchId, Long rowId, String orderNumber, long expectedVersion, String why) {
        access.require(IMPORT_SHIPMENT_RESULTS);
        var batch = batch(batchId);
        var row =
                rows.findByIdAndBatchId(rowId, batchId)
                        .orElseThrow(() -> new WorkflowException(404, "NOT_FOUND", "해당 줄을 찾을 수 없습니다."));
        if (row.getVersion() != expectedVersion) {
            throw new WorkflowException(409, "STALE_VERSION", "화면이 오래되었습니다. 다시 불러와 주세요.");
        }
        if ("COMMITTED".equals(row.getStatus())) {
            throw new WorkflowException(409, "ALREADY_APPLIED", "이미 반영된 줄입니다.");
        }
        var pool = candidatesOf(batch.getOutboundBatchId());
        boolean inPool = pool.stream().anyMatch(c -> c.orderNumber().equals(orderNumber));
        if (!inPool) {
            // 후보 밖의 주문을 손으로 적어 넣는 길을 열지 않는다. 열면 이 묶음과
            // 상관없는 주문에 송장이 붙는다.
            throw new WorkflowException(400, "NOT_IN_BATCH", "이 발송 묶음에 없는 주문입니다.");
        }
        row.resolve(orderNumber, access.current().getId(), clock.instant(), why);
        rows.saveAndFlush(row);
        workflow.audit(
                orderNumber,
                "RESOLVE_POSTAL_IMPORT_ROW",
                null,
                null,
                row.getLineNumber() + "행 수동 선택 · " + (why == null ? "" : why));
        return view(batch, rows.findByBatchIdOrderByLineNumberAsc(batchId), pool);
    }

    /**
     * 고른 줄을 실제 주문에 반영한다.
     *
     * <p>줄마다 따로 판단해 성공과 실패를 그대로 돌려준다. 일부만 되었는데 전부
     * 성공했다고 보여 주면, 빠진 주문은 아무도 모르는 채로 남는다.
     */
    @Transactional
    public Map<String, Object> commit(Long batchId, List<Long> selectedRowIds) {
        access.require(IMPORT_SHIPMENT_RESULTS);
        var batch = batch(batchId);
        var pool = candidatesOf(batch.getOutboundBatchId());
        var now = clock.instant();

        Map<String, Object> results = new LinkedHashMap<>();
        for (Long rowId : selectedRowIds == null ? List.<Long>of() : selectedRowIds) {
            var row = rows.findByIdAndBatchId(rowId, batchId).orElse(null);
            if (row == null) continue;
            results.put(String.valueOf(rowId), applyOne(row, pool, now));
        }
        rows.flush();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("batchId", batchId);
        notifications.batchIdForPostalImport(batchId).ifPresent(id -> response.put("notificationBatchId", id));
        response.put("results", results);
        response.put("rows", rowViews(rows.findByBatchIdOrderByLineNumberAsc(batchId)));
        return response;
    }

    /** 한 줄을 반영한다. 반영 직전에 주문 상태를 다시 본다. */
    private String applyOne(PostalImportRow row, List<PostalNameMatcher.Candidate> pool, java.time.Instant now) {
        if ("COMMITTED".equals(row.getStatus())) return "ALREADY_APPLIED";
        if (!row.isSelectable()) return "NOT_SELECTABLE";
        String orderNumber = row.getMatchedOrderNumber();
        if (orderNumber == null) return "NOT_SELECTABLE";
        if (pool.stream().noneMatch(c -> c.orderNumber().equals(orderNumber))) {
            row.block("이 발송 묶음에 없는 주문입니다.");
            return "NOT_IN_BATCH";
        }

        var order = orders.lockByOrderNumber(orderNumber).orElse(null);
        if (order == null) {
            row.block("주문을 찾을 수 없습니다.");
            return "ORDER_NOT_FOUND";
        }
        if (!GoodsOrderStatus.releasesSlot().contains(order.getStatus())
                && order.isPostOfficeAccepted()) {
            // 같은 송장이 이미 붙어 있으면 성공을 다시 만들지 않는다. 다시 만들면
            // 정산과 알림이 두 번 생긴다.
            if (row.getTrackingNumber().equals(order.getTrackingNumber())) {
                row.markAlreadyApplied("이미 같은 송장이 반영되어 있습니다.");
                return "ALREADY_APPLIED";
            }
            row.block("이 주문에는 다른 송장이 이미 반영되어 있습니다.");
            return "TRACKING_CONFLICT";
        }
        if (GoodsOrderStatus.releasesSlot().contains(order.getStatus())) {
            row.block("취소되었거나 결제가 끝난 주문입니다.");
            return "ORDER_NOT_ACTIVE";
        }
        // 같은 송장번호가 다른 주문에 붙어 있으면 막는다. 둘 중 하나는 실제와
        // 다른 번호를 갖게 된다.
        var clash = orders.findByTrackingNumber(row.getTrackingNumber()).orElse(null);
        if (clash != null && !clash.getOrderNumber().equals(orderNumber)) {
            row.block("같은 송장번호가 다른 주문에 이미 붙어 있습니다.");
            return "TRACKING_TAKEN";
        }

        order.confirmPostOfficeAcceptance(row.getTrackingNumber(), row.getPostageKrw(), now);
        String settlement = compensation.recordAtFulfillment(orderNumber);
        notifications.enqueue(row.getBatchId(), order);
        row.markCommitted(now);
        workflow.audit(
                orderNumber,
                "APPLY_POSTAL_TRACKING",
                "AWAITING_POST_OFFICE_RESULT",
                "ACCEPTED",
                "송장 " + row.getTrackingNumber() + " · 요금 " + row.getPostageKrw()
                        + "원 · 정산 " + settlement);
        return "APPLIED";
    }

    @Transactional(readOnly = true)
    public Map<String, Object> read(Long batchId) {
        access.require(IMPORT_SHIPMENT_RESULTS);
        var batch = batch(batchId);
        return view(
                batch,
                rows.findByBatchIdOrderByLineNumberAsc(batchId),
                candidatesOf(batch.getOutboundBatchId()));
    }

    private PostalImportBatch batch(Long id) {
        return batches
                .findById(id)
                .orElseThrow(() -> new WorkflowException(404, "NOT_FOUND", "붙여넣기 기록을 찾을 수 없습니다."));
    }

    /**
     * 이번에 부친 묶음에 들어 있는 주문들.
     *
     * <p>찾는 범위는 여기까지다. 전체 주문을 이름으로 뒤지면 이 묶음과 상관없는
     * 주문에 송장이 붙는다.
     */
    private List<PostalNameMatcher.Candidate> candidatesOf(Long outboundBatchId) {
        var orderNumbers =
                exportItems.findByBatchIdOrderByRowNumberAsc(outboundBatchId).stream()
                        .map(com.pawever.backend.workflow.ShipmentExportItem::getOrderNumber)
                        .toList();
        if (orderNumbers.isEmpty()) return List.of();
        return orders.findByOrderNumberIn(orderNumbers).stream()
                .filter(o -> o.getDeliveryMethod() == GoodsDeliveryMethod.SHIPPING)
                .filter(o -> !GoodsOrderStatus.releasesSlot().contains(o.getStatus()))
                .map(
                        o ->
                                new PostalNameMatcher.Candidate(
                                        o.getOrderNumber(), o.getGuardianName(), o.getPetName(), o.getPostalCode()))
                .toList();
    }

    private Map<String, Object> view(
            PostalImportBatch batch, List<PostalImportRow> all, List<PostalNameMatcher.Candidate> pool) {
        Map<String, Integer> counts = new HashMap<>();
        all.forEach(r -> counts.merge(r.getStatus(), 1, Integer::sum));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId", batch.getId());
        result.put("outboundBatchId", batch.getOutboundBatchId());
        result.put("rows", rowViews(all));
        result.put("counts", counts);
        result.put(
                "candidates",
                pool.stream()
                        .map(
                                c ->
                                        Map.of(
                                                "orderNumber", c.orderNumber(),
                                                "guardianName", c.guardianName(),
                                                "petName", c.petName(),
                                                "postalCode", c.postalCode()))
                        .toList());
        return result;
    }

    private List<Map<String, Object>> rowViews(List<PostalImportRow> all) {
        List<Map<String, Object>> views = new ArrayList<>();
        for (var row : all) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", row.getId());
            view.put("version", row.getVersion());
            view.put("lineNumber", row.getLineNumber());
            view.put("rawLine", row.getRawLine());
            view.put("trackingNumber", row.getTrackingNumber());
            view.put("postageKrw", row.getPostageKrw());
            view.put("postalCode", row.getPostalCode());
            view.put("recipientLabel", row.getRecipientLabel());
            view.put("status", row.getStatus());
            view.put("matchKind", row.getMatchKind());
            view.put("reason", row.getReason());
            view.put("candidates", row.candidates());
            view.put("matchedOrderNumber", row.getMatchedOrderNumber());
            view.put("selectable", row.isSelectable());
            views.add(view);
        }
        return views;
    }
}
