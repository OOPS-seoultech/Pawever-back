package com.pawever.backend.global.config;

import com.pawever.backend.admin.security.AdminAuthenticationFilter;
import com.pawever.backend.global.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AdminAuthenticationFilter adminAuthenticationFilter;
    private final CorsProperties corsProperties;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/public/goods-survey/**").permitAll()
                        // 내보내기는 로그인 대신 전용 토큰으로 막는다.
                        // 토큰이 설정되지 않으면 컨트롤러가 무조건 거부한다.
                        .requestMatchers("/api/internal/goods-survey/export/**").permitAll()
                        // 발송 표시·수신거부·파기 실행도 같은 토큰으로 막는다.
                        .requestMatchers("/api/internal/goods-survey/fulfillments/**").permitAll()
                        .requestMatchers("/api/internal/goods-survey/notice-subscriptions/**").permitAll()
                        .requestMatchers("/api/internal/goods-survey/retention/**").permitAll()
                        // 앱 통계도 같은 방식. 전용 토큰이 없으면 컨트롤러가 거부한다.
                        .requestMatchers("/api/internal/stats/export/**").permitAll()
                        // 로그인과 초대 수락은 아직 토큰이 없는 상태에서 부른다.
                        // 첫 관리자 세우기는 별도 토큰으로 막고, 관리자가 생기면 스스로 닫힌다.
                        .requestMatchers("/api/admin/auth/**").permitAll()
                        // 나머지 관리자 통로는 역할까지 본다. 화면에서 메뉴만 숨기면
                        // 주소를 직접 치거나 요청을 그대로 보내는 것으로 넘어간다.
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "PRODUCTION")
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .anyRequest().authenticated()
                )
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .addFilterBefore(adminAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** 관리자 비밀번호 저장에 쓴다. 앱 회원은 소셜 로그인이라 비밀번호가 없다. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/public/goods-survey/**", publicSurveyCors());
        source.registerCorsConfiguration("/api/admin/**", adminCors());
        return source;
    }

    /** 굿즈 설문은 로그인 없이 부른다. 로그인 토큰을 받을 이유가 없다. */
    private CorsConfiguration publicSurveyCors() {
        CorsConfiguration configuration = baseCors();
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Content-Type",
                "X-Survey-Edit-Token",
                "Idempotency-Key"
        ));
        return configuration;
    }

    /**
     * 관리자 화면은 랜딩 도메인에서 이 API 도메인을 부른다.
     *
     * 인가 설정이 맞아도 CORS 가 없으면 브라우저가 요청을 보내지 않는다. 서버에는
     * 아무 기록도 남지 않고 화면만 비어 있어서 권한 문제로 오해하기 쉽다.
     *
     * 담당자 비활성화가 DELETE 라 설문 쪽과 허용 메서드가 다르다. 설정을 하나로
     * 합치면 필요 없는 쪽까지 같이 넓어진다.
     */
    private CorsConfiguration adminCors() {
        CorsConfiguration configuration = baseCors();
        configuration.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Authorization"));
        return configuration;
    }

    private CorsConfiguration baseCors() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
        // 토큰을 헤더로 보내므로 쿠키를 실을 이유가 없다.
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        return configuration;
    }
}
