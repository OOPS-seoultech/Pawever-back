package com.pawever.backend.workflow;

import java.util.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
@Order(-1)
public class WorkflowExceptionHandler {
  @ExceptionHandler(WorkflowException.class)
  public ResponseEntity<?> workflow(WorkflowException e) {
    var body = new LinkedHashMap<String, Object>();
    body.put("success", false);
    body.put("code", e.getCode());
    body.put("message", e.getMessage());
    body.put("data", e.getLatest());
    return ResponseEntity.status(e.getStatus()).body(body);
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<?> conflict() {
    return ResponseEntity.status(409)
        .body(
            Map.of(
                "success",
                false,
                "code",
                "VERSION_CONFLICT",
                "message",
                "다른 작업자가 변경했습니다. 새로고침 후 다시 시도해 주세요."));
  }

  @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
  public ResponseEntity<?> missingHeader() {
    return ResponseEntity.badRequest()
        .body(Map.of("success", false, "code", "INVALID_INPUT", "message", "필수 요청 헤더가 없습니다."));
  }
}
