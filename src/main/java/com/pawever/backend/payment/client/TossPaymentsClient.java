package com.pawever.backend.payment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.payment.config.TossPaymentsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;

/**
 * 토스페이먼츠에 묻고 시키는 통로.
 *
 * 승인은 돈이 실제로 빠져나가는 호출이다. 실패와 성공을 뭉뚱그리면 받지도
 * 않은 돈을 받은 것으로 적게 되므로, 성공을 확인한 값만 돌려준다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TossPaymentsClient {

    private final RestTemplate restTemplate;
    private final TossPaymentsProperties properties;

    /**
     * 결제를 승인한다.
     *
     * 이 호출이 성공해야 돈이 빠져나간다. 화면이 성공 주소로 돌아왔다는 것만으로는
     * 아무것도 확정되지 않는다 — 그 주소는 누구나 열 수 있다.
     */
    public TossPayment confirm(String paymentKey, String orderId, int amount) {
        return call(
                "/v1/payments/confirm",
                HttpMethod.POST,
                Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount),
                null,
                ErrorCode.PAYMENT_CONFIRM_FAILED
        );
    }

    /**
     * 결제 상태를 다시 물어본다.
     *
     * 웹훅이 알려준 내용을 그대로 믿지 않기 위해 쓴다. 웹훅 주소는 밖에서
     * 부를 수 있으므로, 받은 값을 사실로 적으면 아무나 결제 완료를 만들 수 있다.
     */
    public TossPayment find(String paymentKey) {
        return call(
                "/v1/payments/" + paymentKey,
                HttpMethod.GET,
                null,
                null,
                ErrorCode.PAYMENT_CONFIRM_FAILED
        );
    }

    /**
     * 결제를 취소한다.
     *
     * 멱등 키를 함께 보낸다. 같은 취소를 두 번 부르면 토스가 앞의 결과를
     * 그대로 돌려주므로, 재시도가 이중 환불이 되지 않는다.
     */
    public TossPayment cancel(String paymentKey, String reason, String idempotencyKey) {
        return call(
                "/v1/payments/" + paymentKey + "/cancel",
                HttpMethod.POST,
                Map.of("cancelReason", reason),
                idempotencyKey,
                ErrorCode.PAYMENT_CANCEL_FAILED
        );
    }

    private TossPayment call(
            String path,
            HttpMethod method,
            Object body,
            String idempotencyKey,
            ErrorCode onFailure
    ) {
        if (!properties.isConfigured()) {
            throw new CustomException(ErrorCode.PAYMENT_NOT_CONFIGURED);
        }

        HttpHeaders headers = new HttpHeaders();
        // 토스는 시크릿 키를 아이디로, 비밀번호를 빈 값으로 두는 Basic 인증을 쓴다.
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder()
                .encodeToString((properties.getSecretKey() + ":").getBytes(StandardCharsets.UTF_8)));
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }

        try {
            return restTemplate.exchange(
                    properties.getBaseUrl() + path,
                    method,
                    new HttpEntity<>(body, headers),
                    TossPayment.class
            ).getBody();
        } catch (RestClientException exception) {
            // 응답 본문에 결제 수단이나 카드 정보가 담겨 올 수 있어 그대로 남기지 않는다.
            log.warn("토스 호출 실패 path={} 사유={}", path, exception.getClass().getSimpleName());
            throw new CustomException(onFailure);
        }
    }

    /**
     * 토스가 돌려주는 결제 한 건.
     *
     * 필요한 것만 받는다. 카드 번호나 결제 수단 상세는 우리가 보관할 이유가 없다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TossPayment(
            String paymentKey,
            String orderId,
            /** DONE, CANCELED, ABORTED, EXPIRED 등. */
            String status,
            /** 실제로 승인된 금액. 우리가 적어 둔 금액과 대조한다. */
            Integer totalAmount,
            /** 카드, 간편결제 같은 값. 관리자 화면에 그대로 보여 준다. */
            String method,
            String approvedAt
    ) {

        public boolean isDone() {
            return "DONE".equals(status);
        }

        public boolean isCanceled() {
            return "CANCELED".equals(status) || "PARTIAL_CANCELED".equals(status);
        }

        /** 승인 시각. 토스가 주지 않으면 부른 쪽이 지금 시각을 쓴다. */
        public Instant approvedInstant() {
            if (approvedAt == null || approvedAt.isBlank()) {
                return null;
            }
            try {
                return OffsetDateTime.parse(approvedAt).toInstant();
            } catch (RuntimeException exception) {
                return null;
            }
        }
    }
}
