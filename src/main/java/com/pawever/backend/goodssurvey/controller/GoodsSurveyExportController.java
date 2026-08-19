package com.pawever.backend.goodssurvey.controller;

import com.pawever.backend.goodssurvey.service.GoodsSurveyExportService;
import com.pawever.backend.goodssurvey.service.GoodsSurveyInternalToken;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

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

    private static final String TOKEN_HEADER = GoodsSurveyInternalToken.HEADER;

    private final GoodsSurveyExportService exportService;
    private final GoodsSurveyInternalToken internalToken;

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

    /** 제작에 넘길 목록. 개인정보를 빼고 만드는 데 필요한 것만 담는다. */
    @GetMapping("/production")
    public ResponseEntity<byte[]> production(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token,
            @RequestParam(defaultValue = "1") int from,
            @RequestParam(defaultValue = "" + Integer.MAX_VALUE) int to
    ) {
        requireToken(token);
        return csv(
                "goods-survey-production-%d-%d.csv".formatted(from, to),
                exportService.productionCsv(from, to)
        );
    }

    /**
     * 제작용 사진 묶음.
     *
     * 사진이 커서 전부 메모리에 담지 않고 흘려보낸다.
     * 한 번에 받기 버거우면 from·to로 나눠 받으면 된다.
     */
    @GetMapping("/photos.zip")
    public ResponseEntity<StreamingResponseBody> photos(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token,
            @RequestParam(defaultValue = "1") int from,
            @RequestParam(defaultValue = "" + Integer.MAX_VALUE) int to
    ) {
        requireToken(token);
        StreamingResponseBody body = output -> exportService.writePhotoArchive(from, to, output);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"goods-survey-photos-%d-%d.zip\"".formatted(from, to)
                )
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    private void requireToken(String token) {
        internalToken.require(token);
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
