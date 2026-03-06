package com.pawever.backend.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 4xx/5xx 응답 또는 느린 요청만 로깅 (파일 request.log로 출력).
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger requestLog = LoggerFactory.getLogger("com.pawever.backend.global.logging.RequestLogging");

    /** 이 시간(ms) 초과 시 느린 요청으로 로깅 */
    private static final long SLOW_THRESHOLD_MS = 3_000;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        StatusCaptureResponse wrapped = new StatusCaptureResponse(response);

        try {
            filterChain.doFilter(request, wrapped);
        } finally {
            int status = wrapped.getCapturedStatus();
            long durationMs = System.currentTimeMillis() - start;
            boolean isError = status >= 400;
            boolean isSlow = durationMs >= SLOW_THRESHOLD_MS;

            if (isError || isSlow) {
                String method = request.getMethod();
                String path = request.getRequestURI();
                Long userId = resolveUserId();
                String logMsg = String.format("%s %s %d %dms userId=%s",
                        method, path, status, durationMs, userId != null ? userId : "-");
                if (isError) {
                    requestLog.warn(logMsg);
                } else {
                    requestLog.info("SLOW " + logMsg);
                }
            }
        }
    }

    private static class StatusCaptureResponse extends HttpServletResponseWrapper {
        private int status = 200;

        StatusCaptureResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setStatus(int sc) {
            this.status = sc;
            super.setStatus(sc);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void setStatus(int sc, String sm) {
            this.status = sc;
            super.setStatus(sc, sm);
        }

        @Override
        public void sendError(int sc) throws IOException {
            this.status = sc;
            super.sendError(sc);
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            this.status = sc;
            super.sendError(sc, msg);
        }

        int getCapturedStatus() {
            return status;
        }
    }

    private Long resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long) {
            return (Long) auth.getPrincipal();
        }
        return null;
    }
}
