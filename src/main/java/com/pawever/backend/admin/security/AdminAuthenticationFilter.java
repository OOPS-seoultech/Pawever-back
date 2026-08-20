package com.pawever.backend.admin.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 관리자 요청에만 도는 인증.
 *
 * /api/admin 아래에서만 동작한다. 다른 경로에서 관리자 토큰을 인정하면
 * 관리자 권한이 서비스 전체로 새어 나간다.
 *
 * 앱 회원 토큰은 서명 키가 달라 여기서 통과하지 못한다.
 */
@Component
@RequiredArgsConstructor
public class AdminAuthenticationFilter extends OncePerRequestFilter {

    private static final String ADMIN_PATH_PREFIX = "/api/admin/";

    private final AdminTokenProvider tokenProvider;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(ADMIN_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            AdminPrincipal principal = tokenProvider.parse(token);
            if (principal != null) {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                List.of(new SimpleGrantedAuthority(principal.role().authority()))
                        )
                );
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
