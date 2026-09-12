package com.pawever.backend.workflow;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "workflow_settings")
@Getter
@NoArgsConstructor
public class WorkflowSettings {
  @Id private Long id = 1L;
  @Version private long version;
  private Long modeling;
  private Long review;

  public void change(Long modeling, Long review) {
    this.modeling = modeling;
    this.review = review;
  }
}
