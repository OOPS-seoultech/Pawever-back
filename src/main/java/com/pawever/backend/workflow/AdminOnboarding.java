package com.pawever.backend.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "admin_onboarding")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminOnboarding {
  @Id private Long accountId;
  @Column(nullable = false, length = 20) private String decision;
  @Column(nullable = false) private Instant decidedAt;

  public static AdminOnboarding of(Long account, String decision, Instant at) {
    var item = new AdminOnboarding();
    item.accountId = account; item.decision = decision; item.decidedAt = at;
    return item;
  }
}
