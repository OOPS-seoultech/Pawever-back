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
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Content-Type",
                "X-Survey-Edit-Token",
                "Idempotency-Key"
        ));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/public/goods-survey/**", configuration);
        return source;
    }
}
