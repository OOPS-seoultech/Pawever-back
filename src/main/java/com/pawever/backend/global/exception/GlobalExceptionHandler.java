package com.pawever.backend.global.exception;

import com.pawever.backend.global.common.ApiResponse;
import com.pawever.backend.global.event.ApiContractBreachEvent;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ApplicationEventPublisher eventPublisher;

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        log.info("커스텀 예외 처리: status={}, message={}", e.getErrorCode().getHttpStatus(), e.getMessage());
        return ResponseEntity
                .status(e.getErrorCode().getHttpStatus())
                .body(ApiResponse.error(e.getErrorCode().name(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT.name(), message));
    }

    /**
     * 본문을 읽지도 못한 요청.
     *
     * 값 하나가 틀린 것과 다르다. 여기까지 오면 우리가 적어 둔 검증은 한 줄도
     * 돌지 않고, 답에도 어느 항목이 문제인지 담기지 않는다. 화면 쪽 모양이
     * 어긋난 것이므로 그 화면에서 오는 요청은 한 건도 통과하지 못한다.
     *
     * 그래서 INFO 가 아니라 WARN 으로, 어느 경로였는지와 함께 남긴다. 2026-09-16
     * 에 신청 폼이 원시 boolean 하나를 빠뜨렸을 때 여기에 답이 적혀 있었지만,
     * INFO 로 흘러가 엿새 동안 아무도 보지 않았다.
     *
     * 까닭은 로그에만 둔다. 본문 조각이 섞여 나올 수 있고 그 안에는 이름과
     * 연락처가 있다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(
            HttpMessageNotReadableException e,
            HttpServletRequest request
    ) {
        String path = request.getMethod() + " " + request.getRequestURI();
        log.warn("본문을 읽지 못해 거절했다: {} - {}", path, e.getMessage());
        eventPublisher.publishEvent(new ApiContractBreachEvent(path));
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT.name(), ErrorCode.INVALID_INPUT.getMessage()));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        log.info("잘못된 요청: {}", e.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT.name(), ErrorCode.INVALID_INPUT.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.info("업로드 용량 초과: {}", e.getMessage());
        return ResponseEntity
                .status(ErrorCode.FILE_TOO_LARGE.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.FILE_TOO_LARGE.name(), ErrorCode.FILE_TOO_LARGE.getMessage()));
    }

    /**
     * 역할이 모자라 막힌 것.
     *
     * <p>@PreAuthorize 는 컨트롤러 안에서 터지므로 시큐리티 필터가 아니라 여기로
     * 온다. 받아 주지 않으면 아래 모두잡이로 떨어져 500 이 나가고, 로그에는
     * 서버가 깨진 것처럼 남는다. 실제로는 서버가 제대로 막은 것이다.
     *
     * <p>401 로 바꾸지 않는다. 다시 로그인해도 같은 곳이 막혀 있어, 화면이
     * 로그인 화면으로 보내면 끝나지 않는 고리가 된다.
     */
    @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(Exception e) {
        log.info("권한 없음: {}", e.getMessage());
        return ResponseEntity
                .status(ErrorCode.FORBIDDEN.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.FORBIDDEN.name(), ErrorCode.FORBIDDEN.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("처리되지 않은 서버 예외", e);
        return ResponseEntity
                .internalServerError()
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.getMessage()));
    }
}
