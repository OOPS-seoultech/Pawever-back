package com.pawever.backend.workflow;

import com.pawever.backend.admin.entity.PermissionKey;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(
    name = "account_permission_overrides",
    uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "permission_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffPermissionOverride {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long accountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 60)
  private PermissionKey permissionKey;

  @Column(nullable = false)
  private boolean allowed;

  private Instant expiresAt;

  @Column(nullable = false, length = 300)
  private String reason;

  public static StaffPermissionOverride of(
      Long account, PermissionKey permission, boolean allowed, Instant expires, String reason) {
    var o = new StaffPermissionOverride();
    o.accountId = account;
    o.permissionKey = permission;
    o.allowed = allowed;
    o.expiresAt = expires;
    o.reason = reason;
    return o;
  }
}
