package com.pawever.backend.workflow.postal;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 우체국 접수 내역 붙여넣기.
 *
 * <p>미리보기는 주문을 바꾸지 않는다. 실제로 송장이 붙는 것은 확정 한 번뿐이다.
 */
@RestController
@RequestMapping("/api/admin/postal-imports")
@RequiredArgsConstructor
public class PostalImportController {

    private final PostalImportService service;

    /** 붙여넣은 내역을 읽어 무엇이 자동이고 무엇이 애매한지 보여 준다. */
    @PostMapping("/preview")
    public Map<String, Object> preview(@RequestBody Map<String, Object> body) {
        return service.preview(asLong(body.get("outboundBatchId")), text(body.get("text")));
    }

    @GetMapping("/{batchId}")
    public Map<String, Object> read(@PathVariable Long batchId) {
        return service.read(batchId);
    }

    /** 애매한 줄을 사람이 고른다. */
    @PostMapping("/{batchId}/rows/{rowId}/resolve")
    public Map<String, Object> resolve(
            @PathVariable Long batchId, @PathVariable Long rowId, @RequestBody Map<String, Object> body) {
        return service.resolve(
                batchId,
                rowId,
                text(body.get("orderNumber")),
                asLong(body.get("expectedVersion")),
                text(body.get("reason")));
    }

    /** 고른 줄을 실제 주문에 반영한다. */
    @PostMapping("/{batchId}/commit")
    @SuppressWarnings("unchecked")
    public Map<String, Object> commit(@PathVariable Long batchId, @RequestBody Map<String, Object> body) {
        Object selected = body.get("selectedRowIds");
        List<Long> ids =
                selected instanceof List<?> list
                        ? list.stream().map(PostalImportController::asLong).filter(java.util.Objects::nonNull).toList()
                        : List.of();
        return service.commit(batchId, ids);
    }

    private static Long asLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
