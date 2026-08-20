package com.pawever.backend.admin.controller;

import com.pawever.backend.admin.dto.AdminAcceptInviteRequest;
import com.pawever.backend.admin.dto.AdminBootstrapRequest;
import com.pawever.backend.admin.dto.AdminSignInRequest;
import com.pawever.backend.admin.service.AdminAccountService;
import com.pawever.backend.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 로그인하기 전에 닿을 수 있는 통로.
 *
 * 로그인과 초대 수락은 아직 토큰이 없는 상태에서 부른다. 그래서 인증 없이
 * 열어 두되, 여기서 할 수 있는 일은 이 둘뿐이다.
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private static final String BOOTSTRAP_TOKEN_HEADER = "X-Admin-Bootstrap-Token";

    private final AdminAccountService accountService;

    @PostMapping("/sign-in")
    public ApiResponse<Map<String, String>> signIn(@Valid @RequestBody AdminSignInRequest request) {
        String token = accountService.signIn(request.email(), request.password());
        return ApiResponse.ok(Map.of("accessToken", token));
    }

    /** 초대 링크로 들어와 비밀번호를 정한다. */
    @PostMapping("/accept-invite")
    public ApiResponse<Void> acceptInvite(@Valid @RequestBody AdminAcceptInviteRequest request) {
        accountService.acceptInvite(request.inviteToken(), request.password());
        return ApiResponse.ok();
    }

    /**
     * 첫 관리자를 세운다.
     *
     * 관리자가 하나라도 생기면 이 통로는 스스로 닫힌다. 돌려주는 초대 값은
     * 지금 한 번만 볼 수 있다.
     */
    @PostMapping("/bootstrap")
    public ApiResponse<Map<String, String>> bootstrap(
            @RequestHeader(value = BOOTSTRAP_TOKEN_HEADER, required = false) String bootstrapToken,
            @Valid @RequestBody AdminBootstrapRequest request
    ) {
        String inviteToken = accountService.bootstrapFirstAdmin(
                bootstrapToken,
                request.email(),
                request.name()
        );
        return ApiResponse.ok(Map.of("inviteToken", inviteToken));
    }
}
