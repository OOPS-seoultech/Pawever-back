package com.pawever.backend.workflow;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "workflow_issues",
    uniqueConstraints = @UniqueConstraint(columnNames = {"order_number", "code"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkflowIssue {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 20)
  private String orderNumber;

  @Column(nullable = false, length = 40)
  private String code;

  public static WorkflowIssue of(String number, String code) {
    var i = new WorkflowIssue();
    i.orderNumber = number;
    i.code = code;
    return i;
  }
}
