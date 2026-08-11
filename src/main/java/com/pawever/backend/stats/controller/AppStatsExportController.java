package com.pawever.backend.stats.controller;

import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.stats.config.AppStatsProperties;
import com.pawever.backend.stats.service.AppStatsExportService;
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
 * 앱 통계 내보내기.
 *
 * 집계값만 나가지만 서비스 규모가 그대로 드러나는 통로다.
 * 설문 내보내기와 같은 방식으로 전용 토큰을 요구한다.
 */
@RestController
@RequestMapping("/api/internal/stats/export")
@RequiredArgsConstructor
public class AppStatsExportController {

    private static final String TOKEN_HEADER = "X-Stats-Export-Token";

    private final AppStatsExportService exportService;
    private final AppStatsProperties properties;

    @GetMapping("/summary")
    public ResponseEntity<byte[]> summary(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token
    ) {
        requireToken(token);
        return csv("app-stats-summary.csv", exportService.summaryCsv());
    }

    private void requireToken(String token) {
        String expected = properties.getExportToken();
        if (expected == null || expected.isBlank() || token == null) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        // 길이로 정답을 흘리지 않도록 상수 시간 비교를 쓴다.
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
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body.getBytes(StandardCharsets.UTF_8));
    }
}
