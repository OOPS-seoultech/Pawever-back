package com.pawever.backend.workflow;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "production_compensation_settings")
@Getter
@NoArgsConstructor
public class ProductionCompensationSettings {
  @Id private Long id = 1L;
  @Version private long version;

  @Column(nullable = false)
  private boolean enabled;

  @Column(nullable = false)
  private long revision;

  public void change(boolean enabled) {
    this.enabled = enabled;
    revision++;
  }
}
