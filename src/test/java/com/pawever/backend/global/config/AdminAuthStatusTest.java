package com.pawever.backend.global.config;

import com.pawever.backend.admin.entity.AdminRole;
import com.pawever.backend.admin.security.AdminTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로그인이 풀린 것과 권한이 없는 것을 다른 번호로 알린다.
 *
 * <p>관리자 토큰은 여덟 시간이면 끝난다. 플리마켓처럼 하루 종일 여는 자리에서는
 * 반드시 중간에 끝나고, 그때부터 화면은 부르는 것마다 막힌다.
 *
 * <p>스프링 시큐리티는 로그인 통로를 하나도 두지 않으면 로그인하지 않은 요청에도
 * 403 을 준다. 그러면 화면은 "다시 로그인하세요"와 "당신에게는 안 열린 곳입니다"를
 * 가릴 수 없다. 담당자는 빨간 오류만 보고 서버가 죽은 줄 안다.
 *
 * <p>규칙은 HTTP 가 이미 정해 두었다. 401 은 네가 누구인지 모르겠다는 뜻이고,
 * 403 은 누구인지는 알지만 여기는 아니라는 뜻이다. 화면은 앞의 것에만 로그인
 * 화면을 띄운다.
 */
@SpringBootTest(properties =
        // 역할 검사까지 가려면 진짜 토큰이 필요하다. 테스트 프로필은
        // 서명 키를 비워 두므로 여기서 하나 준다.
        "admin.jwt-secret=admin-secret-key-must-be-at-least-256-bits-long-for-hs256-ok")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuthStatusTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AdminTokenProvider adminTokens;

    @Test
    void 토큰이_없으면_401_이다() throws Exception {
        mockMvc.perform(get("/api/admin/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 토큰이_상했으면_401_이다() throws Exception {
        // 여덟 시간이 지난 토큰도 서버가 보기에는 이것과 같다. 서명이나 기한이
        // 어긋난 값은 사람을 알아볼 수 없으므로 로그인부터 다시다.
        mockMvc.perform(get("/api/admin/orders")
                        .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.bad"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 401 로 바꾸면서 이쪽까지 401 이 되면 안 된다. 제작팀이 담당자 관리를
     * 열었을 때 로그인 화면으로 보내면, 다시 로그인해도 같은 곳이 막혀 있어
     * 끝나지 않는 고리가 된다.
     */
    @Test
    void 역할이_모자라면_403_이다() throws Exception {
        String token = adminTokens.createToken(1L, AdminRole.PRODUCTION);

        mockMvc.perform(get("/api/admin/accounts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
