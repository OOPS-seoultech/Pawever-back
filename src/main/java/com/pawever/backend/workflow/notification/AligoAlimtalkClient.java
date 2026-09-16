package com.pawever.backend.workflow.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/** 알리고 알림톡 요청 접수와 수신자별 최종 결과 조회를 분리한 어댑터. */
@Component
@RequiredArgsConstructor
public class AligoAlimtalkClient {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final RestTemplate restTemplate;
  private final ShipmentNotificationProperties properties;

  public ProviderResult send(String phone, String subject, String message) {
    MultiValueMap<String, String> form = credentials();
    form.add("senderkey", properties.getSenderKey());
    form.add("tpl_code", properties.getTemplateCode());
    form.add("sender", properties.getSender());
    form.add("receiver_1", digitsOnly(phone));
    form.add("subject_1", subject);
    form.add("message_1", message);
    // SMS 대체 발송은 승인 문구와 별개의 메시지가 될 수 있어 기본적으로 금지한다.
    form.add("failover_1", "N");
    if (properties.isTestMode()) form.add("testMode", "Y");

    try {
      JsonNode body = post("/akv10/alimtalk/send/", form);
      if (body == null) return ProviderResult.unknown(null, "INVALID_RESPONSE", "응답을 읽지 못했습니다.");
      String code = body.path("code").asText("");
      if (!"0".equals(code)) {
        return ProviderResult.failed(code, safeMessage(body));
      }
      String mid = body.path("info").path("mid").asText("");
      return mid.isBlank()
          ? ProviderResult.unknown(null, code, "접수 ID가 없습니다.")
          : ProviderResult.accepted(mid, code, safeMessage(body));
    } catch (RestClientException exception) {
      // 타임아웃은 서버가 받았는지 알 수 없다. FAILED로 두고 재전송하면 중복될 수 있다.
      return ProviderResult.unknown(null, "TRANSPORT_ERROR", "제공자 응답을 확인하지 못했습니다.");
    }
  }

  public ProviderResult query(String messageId, String phone) {
    MultiValueMap<String, String> form = credentials();
    form.add("mid", messageId);
    form.add("page", "1");
    form.add("limit", "50");
    try {
      JsonNode body = post("/akv10/history/detail/", form);
      if (body == null || !"0".equals(body.path("code").asText(""))) {
        return ProviderResult.unknown(messageId, "QUERY_ERROR", "결과 조회에 실패했습니다.");
      }
      String normalized = digitsOnly(phone);
      for (JsonNode row : body.path("list")) {
        if (!normalized.equals(digitsOnly(row.path("phone").asText("")))) continue;
        String messageDetailId = row.path("msgid").asText("");
        String resultCode = row.path("rslt").asText("");
        String resultMessage = row.path("rslt_message").asText(row.path("status").asText(""));
        if (messageDetailId.startsWith("Q") || resultCode.isBlank()) {
          return ProviderResult.unknown(messageId, resultCode, resultMessage);
        }
        if (properties.isSuccessCode(resultCode)) {
          return ProviderResult.succeeded(messageId, resultCode, resultMessage);
        }
        return ProviderResult.failed(messageId, resultCode, resultMessage);
      }
      return ProviderResult.unknown(messageId, "NOT_REPORTED", "아직 수신 결과가 없습니다.");
    } catch (RestClientException exception) {
      return ProviderResult.unknown(messageId, "TRANSPORT_ERROR", "제공자 응답을 확인하지 못했습니다.");
    }
  }

  private JsonNode post(String path, MultiValueMap<String, String> form) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    String response =
        restTemplate
            .postForEntity(
                properties.getBaseUrl() + path, new HttpEntity<>(form, headers), String.class)
            .getBody();
    if (response == null || response.isBlank()) return null;
    try {
      return MAPPER.readTree(response);
    } catch (Exception invalidJson) {
      return null;
    }
  }

  private MultiValueMap<String, String> credentials() {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("apikey", properties.getApiKey());
    form.add("userid", properties.getUserId());
    return form;
  }

  private static String safeMessage(JsonNode body) {
    String message = body.path("message").asText("");
    return message.length() <= 300 ? message : message.substring(0, 300);
  }

  private static String digitsOnly(String phone) {
    return phone == null ? "" : phone.replaceAll("[^0-9]", "");
  }

  public enum ResultStatus {
    ACCEPTED,
    SUCCEEDED,
    FAILED,
    UNKNOWN
  }

  public record ProviderResult(
      ResultStatus status, String messageId, String resultCode, String resultMessage) {
    static ProviderResult accepted(String mid, String code, String message) {
      return new ProviderResult(ResultStatus.ACCEPTED, mid, code, message);
    }

    static ProviderResult succeeded(String mid, String code, String message) {
      return new ProviderResult(ResultStatus.SUCCEEDED, mid, code, message);
    }

    static ProviderResult failed(String code, String message) {
      return failed(null, code, message);
    }

    static ProviderResult failed(String mid, String code, String message) {
      return new ProviderResult(ResultStatus.FAILED, mid, code, message);
    }

    static ProviderResult unknown(String mid, String code, String message) {
      return new ProviderResult(ResultStatus.UNKNOWN, mid, code, message);
    }
  }
}
