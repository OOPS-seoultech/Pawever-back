package com.pawever.backend.goodssurvey.service;

import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.goodssurvey.config.GoodsSurveyProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 설문 내부 통로의 토큰 검사.
 *
 * 이름·연락처·주소가 오가거나 개인정보를 파기하는 통로에 걸린다. 토큰이
 * 설정되지 않았거나 맞지 않으면 무조건 막는다. 값 비교는 길이로 정답을
 * 흘리지 않도록 상수 시간 비교를 쓴다.
 */
@Component
@RequiredArgsConstructor
public class GoodsSurveyInternalToken {

    public static final String HEADER = "X-Survey-Export-Token";

    private final GoodsSurveyProperties properties;

    public void require(String token) {
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
}
