package com.pawever.backend.workflow;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "workflow_issues",
    uniqueConstraints = @UniqueConstraint(columnNames = {"order_number", "pet_index", "code"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkflowIssue {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 20)
  private String orderNumber;

  @Column(nullable = false)
  private int petIndex;

  @Column(nullable = false, length = 40)
  private String code;

  public static WorkflowIssue of(String number, int petIndex, String code) {
    var i = new WorkflowIssue();
    i.orderNumber = number;
    i.petIndex = petIndex;
    i.code = code;
    return i;
  }
}
