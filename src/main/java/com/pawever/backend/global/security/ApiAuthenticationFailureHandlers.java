package com.pawever.backend.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawever.backend.global.common.ApiResponse;
import com.pawever.backend.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 로그인이 풀린 것과 권한이 없는 것을 다른 번호로 알린다.
 *
 * <p>스프링 시큐리티는 로그인 통로(폼, 베이직)를 하나도 두지 않으면 로그인하지
 * 않은 요청에도 403 을 준다. 우리는 토큰만 쓰므로 그 통로가 없고, 그래서 지금까지
 * 토큰이 없을 때도 403 이 나갔다.
 *
 * <p>그러면 화면이 두 가지를 가릴 수 없다. 관리자 토큰은 여덟 시간이면 끝나는데,
 * 플리마켓처럼 하루 종일 여는 자리에서는 반드시 중간에 끝난다. 그때 화면은 다시
 * 로그인시켜야 하지만, 제작팀이 담당자 관리를 열었을 때는 로그인시켜서는 안 된다 —
 * 다시 로그인해도 같은 곳이 막힌다.
 *
 * <p>규칙은 HTTP 가 이미 정해 두었다. 401 은 네가 누구인지 모르겠다는 뜻이고,
 * 403 은 누구인지는 알지만 여기는 아니라는 뜻이다.
 *
 * <p>몸통은 애플리케이션이 쓰는 봉투 모양 그대로 보낸다. 시큐리티가 걸러낸
 * 요청만 컨트롤러를 거치지 않아 다른 모양으로 나가면, 화면은 오류 하나를 두
 * 가지 방법으로 읽어야 한다.
 */
@Slf4j
public final class ApiAuthenticationFailureHandlers {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ApiAuthenticationFailureHandlers() {
    }

    /** 누구인지 모를 때. 화면은 이것만 보고 로그인 화면으로 보낸다. */
    public static AuthenticationEntryPoint unauthenticated() {
        return (request, response, exception) -> write(response, ErrorCode.UNAUTHORIZED);
    }

    /** 누구인지는 아는데 여기는 아닐 때. 다시 로그인해도 달라지지 않는다. */
    public static AccessDeniedHandler forbidden() {
        return (request, response, exception) -> write(response, ErrorCode.FORBIDDEN);
    }

    private static void write(HttpServletResponse response, ErrorCode error) throws IOException {
        // 이미 내보내기 시작했으면 손댈 수 없다. 여기서 다시 쓰면 예외가 나고,
        // 그 예외가 원래 오류를 덮는다.
        if (response.isCommitted()) {
            log.warn("응답이 이미 나가 {} 를 적지 못했다", error.name());
            return;
        }

        response.setStatus(error.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                MAPPER.writeValueAsString(
                        ApiResponse.error(error.name(), error.getMessage())));
    }
}
