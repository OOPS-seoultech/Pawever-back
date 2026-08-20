package com.pawever.backend.admin.security;

import com.pawever.backend.admin.entity.AdminRole;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 지금 요청을 보낸 관리자.
 *
 * @param accountId 관리자 계정 식별자. 상태 변경 이력에 남길 담당자다
 * @param role      이 요청에 허용된 범위
 */
public record AdminPrincipal(Long accountId, AdminRole role) {

    /** 관리자 인증을 거치지 않은 요청에서 부르면 null 이 나온다. */
    public static AdminPrincipal current() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AdminPrincipal principal)) {
            return null;
        }
        return principal;
    }
}
