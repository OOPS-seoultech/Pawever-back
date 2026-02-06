package com.pawever.backend.global.security;

import org.springframework.security.core.context.SecurityContextHolder;

public class UserPrincipal {

    public static Long getCurrentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
