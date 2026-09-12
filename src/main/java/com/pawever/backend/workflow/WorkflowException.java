package com.pawever.backend.workflow;

import lombok.Getter;

@Getter
public class WorkflowException extends RuntimeException {
  private final int status;
  private final String code;
  private final Object latest;

  public WorkflowException(int status, String code, String message) {
    this(status, code, message, null);
  }

  public WorkflowException(int status, String code, String message, Object latest) {
    super(message);
    this.status = status;
    this.code = code;
    this.latest = latest;
  }
}
