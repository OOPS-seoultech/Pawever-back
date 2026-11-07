package com.pawever.backend.workflow;

import com.pawever.backend.admin.entity.AdminRole;
import com.pawever.backend.admin.entity.PermissionKey;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class AdminNoticeService {
  private final AdminNoticeRepository notices;
  private final AdminOnboardingRepository onboarding;
  private final StaffPermissions access;
  private final WorkflowService workflow;
  private final Clock clock;

  private void writer() {
    if (access.current().getRole() != AdminRole.OWNER)
      throw new WorkflowException(403, "FORBIDDEN", "대표 계정만 공지사항을 작성할 수 있습니다.");
    access.require(PermissionKey.MANAGE_OPERATION_SETTINGS);
  }

  private Map<String, Object> view(AdminNotice item, boolean full) {
    var result = new LinkedHashMap<String, Object>();
    result.put("id", item.getId()); result.put("version", item.getVersion());
    result.put("title", item.getTitle()); result.put("guideKey", item.getGuideKey());
    result.put("authorName", "PAW-EVER 운영팀");
    result.put("createdAt", item.getCreatedAt()); result.put("updatedAt", item.getUpdatedAt());
    if (full) result.put("body", item.getBody());
    return result;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> list() {
    access.current();
    return notices.findAllByOrderByIdDesc().stream().map(n -> view(n, false)).toList();
  }

  @Transactional(readOnly = true)
  public Map<String, Object> detail(Long id) {
    access.current();
    return view(notices.findById(id).orElseThrow(() -> new WorkflowException(404, "NOT_FOUND", "공지를 찾을 수 없습니다.")), true);
  }

  private String text(Map<String, Object> body, String field, int max) {
    if (!(body.get(field) instanceof String value) || value.isBlank() || value.length() > max
        || value.indexOf(0) >= 0) throw new WorkflowException(400, "INVALID_INPUT", field + " 내용을 확인해 주세요.");
    return value.strip();
  }

  public Map<String, Object> save(Long id, String key, Map<String, Object> body) {
    writer();
    return workflow.command("notice:" + id, key, body, () -> {
      writer();
      String title = text(body, "title", 200), content = text(body, "body", 20_000);
      AdminNotice notice;
      if (id == null) notice = AdminNotice.create(title, content, access.current().getId(), clock.instant());
      else {
        notice = notices.findById(id).orElseThrow(() -> new WorkflowException(404, "NOT_FOUND", "공지를 찾을 수 없습니다."));
        long version;
        try { version = new BigDecimal(Objects.toString(body.get("version"), "")).longValueExact(); }
        catch (NumberFormatException | ArithmeticException e) { throw new WorkflowException(400, "INVALID_INPUT", "공지 버전을 확인해 주세요."); }
        if (notice.getVersion() != version) throw new WorkflowException(409, "VERSION_CONFLICT", "공지가 변경됐습니다. 다시 불러와 주세요.");
        notice.edit(title, content, clock.instant());
      }
      notices.saveAndFlush(notice);
      workflow.audit("notice:" + notice.getId(), id == null ? "CREATE_NOTICE" : "EDIT_NOTICE", null, notice.getTitle(), null);
      return view(notice, true);
    });
  }

  @Transactional(readOnly = true)
  public Map<String, Object> firstVisit() {
    var result = new LinkedHashMap<String, Object>();
    var account = access.current();
    var guide = notices.findByGuideKey("local-folder-guide").orElse(null);
    result.put("notice", onboarding.existsById(account.getId()) || guide == null ? null : view(guide, true));
    return result;
  }

  public Map<String, Object> decide(String key, Map<String, Object> body) {
    access.current();
    return workflow.command("notice-onboarding", key, body, () -> {
      var account = access.current();
      String decision = text(body, "decision", 20);
      if (!Set.of("OPENED", "DEFERRED").contains(decision))
        throw new WorkflowException(400, "INVALID_INPUT", "안내 선택을 확인해 주세요.");
      if (!onboarding.existsById(account.getId()))
        onboarding.saveAndFlush(AdminOnboarding.of(account.getId(), decision, clock.instant()));
      return Map.of("recorded", true);
    });
  }
}
