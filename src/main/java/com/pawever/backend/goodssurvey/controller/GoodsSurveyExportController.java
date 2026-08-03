package com.pawever.backend.goodssurvey.controller;

import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.goodssurvey.config.GoodsSurveyProperties;
import com.pawever.backend.goodssurvey.service.GoodsSurveyExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 설문 결과 내보내기.
 *
 * 이름·연락처·주소가 그대로 나가는 통로다. 토큰이 설정되지 않았거나
 * 맞지 않으면 무조건 막는다. 값 비교는 길이로 정답을 흘리지 않도록
 * 상수 시간 비교를 쓴다.
 */
@RestController
@RequestMapping("/api/internal/goods-survey/export")
@RequiredArgsConstructor
public class GoodsSurveyExportController {

    private static final String TOKEN_HEADER = "X-Survey-Export-Token";

    private final GoodsSurveyExportService exportService;
    private final GoodsSurveyProperties properties;

    @GetMapping("/applications")
    public ResponseEntity<byte[]> applications(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token
    ) {
        requireToken(token);
        return csv("goods-survey-applications.csv", exportService.applicationsCsv());
    }

    @GetMapping("/responses")
    public ResponseEntity<byte[]> responses(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token
    ) {
        requireToken(token);
        return csv("goods-survey-responses.csv", exportService.responsesCsv());
    }

    @GetMapping("/stories")
    public ResponseEntity<byte[]> stories(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token
    ) {
        requireToken(token);
        return csv("goods-survey-stories.csv", exportService.storiesCsv());
    }

    private void requireToken(String token) {
        String expected = properties.getExportToken();
        if (expected == null || expected.isBlank() || token == null) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        boolean matches = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8)
        );
        if (!matches) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    private ResponseEntity<byte[]> csv(String fileName, String body) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\""
                )
                // 개인정보가 담긴 응답이다. 어디에도 남지 않게 한다.
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body.getBytes(StandardCharsets.UTF_8));
    }
}
