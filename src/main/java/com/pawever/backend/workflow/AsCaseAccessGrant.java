package com.pawever.backend.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "as_case_access_grants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AsCaseAccessGrant {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long asCaseId;

  @Column(nullable = false)
  private Long recipientId;

  @Column(nullable = false)
  private Instant activatedAt;

  @Column(nullable = false)
  private Instant expiresAt;

  private Instant revokedAt;
  private Long revokedBy;
  private Long replacedGrantId;

  @Column(nullable = false)
  private Long grantedBy;

  public static AsCaseAccessGrant activate(
      Long caseId, Long recipientId, Long actorId, Long replacedGrantId, Instant now) {
    var value = new AsCaseAccessGrant();
    value.asCaseId = caseId;
    value.recipientId = recipientId;
    value.grantedBy = actorId;
    value.replacedGrantId = replacedGrantId;
    value.activatedAt = now;
    value.expiresAt = now.plusSeconds(48 * 60 * 60);
    return value;
  }

  public boolean isActiveAt(Instant now) {
    return revokedAt == null && expiresAt.isAfter(now);
  }

  public void revoke(Long actorId, Instant now) {
    if (revokedAt == null) {
      revokedAt = now;
      revokedBy = actorId;
    }
  }
}
