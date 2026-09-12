package com.pawever.backend.workflow;

import static com.pawever.backend.admin.entity.PermissionKey.*;

import com.pawever.backend.admin.entity.*;
import com.pawever.backend.admin.repository.AdminAccountRepository;
import com.pawever.backend.admin.security.AdminPrincipal;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StaffPermissions {
  private final AdminAccountRepository accounts;
  private final StaffPermissionOverrideRepository overrides;
  private final ProductionTaskRepository tasks;
  private final Clock clock;

  public AdminAccount current() {
    var p = AdminPrincipal.current();
    if (p == null) throw new WorkflowException(401, "UNAUTHORIZED", "로그인이 필요합니다.");
    var a =
        accounts
            .findById(p.accountId())
            .orElseThrow(() -> new WorkflowException(401, "UNAUTHORIZED", "다시 로그인해 주세요."));
    if (!a.canSignIn()) throw new WorkflowException(401, "UNAUTHORIZED", "사용할 수 없는 계정입니다.");
    return a;
  }

  public Set<PermissionKey> effective(AdminAccount a) {
    var p = EnumSet.noneOf(PermissionKey.class);
    if (a.getRole() == AdminRole.OWNER || a.getRole() == AdminRole.ADMIN) {
      p.addAll(EnumSet.allOf(PermissionKey.class));
      if (a.getRole() != AdminRole.OWNER) p.remove(MANAGE_ACCOUNT_PERMISSIONS);
    }
    if (a.getRole() == AdminRole.PRODUCTION && !a.getWorkRoles().isEmpty()) {
      p.addAll(
          EnumSet.of(
              VIEW_ORDER_BASIC,
              VIEW_CUSTOMER_PHOTOS,
              DOWNLOAD_CUSTOMER_PHOTOS,
              VIEW_PRODUCTION_FILES,
              DOWNLOAD_PRODUCTION_FILES));
      if (a.getWorkRoles().contains(WorkRole.MODELING)) p.add(COMPLETE_MODELING);
      if (a.getWorkRoles().contains(WorkRole.DESIGN_QC)) p.add(REVIEW_MODEL);
    }
    for (var o : overrides.findByAccountId(a.getId()))
      if (o.getExpiresAt() == null || o.getExpiresAt().isAfter(clock.instant())) {
        if (o.isAllowed()) p.add(o.getPermissionKey());
        else p.remove(o.getPermissionKey());
      }
    // Action grants never compensate for denied prerequisite data access.
    if (!p.contains(VIEW_CUSTOMER_PHOTOS)) p.remove(DOWNLOAD_CUSTOMER_PHOTOS);
    if (!p.contains(VIEW_PRODUCTION_FILES)) p.remove(DOWNLOAD_PRODUCTION_FILES);
    if (!p.contains(VIEW_ORDER_BASIC)
        || !p.contains(VIEW_CUSTOMER_PHOTOS)
        || !p.contains(VIEW_PRODUCTION_FILES)) {
      p.remove(COMPLETE_MODELING);
      p.remove(REVIEW_MODEL);
    }
    if (!p.contains(VIEW_PAYMENT) || !p.contains(VIEW_ORDER_BASIC)) p.remove(CONFIRM_PAYMENT);
    if (!p.contains(VIEW_ORDER_BASIC) || !p.contains(VIEW_SHIPMENT))
      p.remove(IMPORT_SHIPMENT_RESULTS);
    if (!p.contains(VIEW_ORDER_BASIC)
        || !p.contains(VIEW_CUSTOMER_IDENTITY)
        || !p.contains(VIEW_SHIPMENT)) p.remove(COMPLETE_PICKUP);
    return p;
  }

  public boolean has(PermissionKey p) {
    return effective(current()).contains(p);
  }

  public AdminAccount require(PermissionKey p) {
    var a = current();
    if (!effective(a).contains(p)) throw new WorkflowException(403, "FORBIDDEN", "이 작업의 권한이 없습니다.");
    return a;
  }

  public boolean canRead(String number) {
    var a = current();
    return effective(a).contains(VIEW_ORDER_BASIC)
        && (effective(a).contains(VIEW_ALL_ORDERS)
            || tasks.existsByOrderNumberAndAssigneeId(number, a.getId()));
  }

  public void read(String number) {
    if (!canRead(number)) throw new WorkflowException(404, "NOT_FOUND", "주문을 찾을 수 없습니다.");
  }

  public Long eligible(Long id, WorkRole role) {
    if (id == null) return null;
    var a = accounts.findById(id).orElse(null);
    return a != null && a.canSignIn() && a.getWorkRoles().contains(role) ? id : null;
  }
}
