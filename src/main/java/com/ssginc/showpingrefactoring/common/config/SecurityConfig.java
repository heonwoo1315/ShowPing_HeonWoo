package com.ssginc.showpingrefactoring.common.config;

import com.ssginc.showpingrefactoring.common.exception.CustomAccessDeniedHandler;
import com.ssginc.showpingrefactoring.common.exception.CustomAuthenticationEntryPoint;
import com.ssginc.showpingrefactoring.common.jwt.*;

import com.ssginc.showpingrefactoring.common.util.MfaGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect;


import java.util.List;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {
    private final JwtFilter jwtFilter;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    private final MfaGuard mfaGuard;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        // --- CSRF 토큰 저장소 + RequestAttribute 핸들러 설정(지연 생성 보강) ---
        var csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookiePath("/");
        var csrfAttrHandler = new CsrfTokenRequestAttributeHandler();
        csrfAttrHandler.setCsrfRequestAttributeName("_csrf"); // 명시(권장)

        http
                // CORS 설정
                .cors(cors -> cors.configurationSource(request -> {
                    CorsConfiguration config = new CorsConfiguration();
                    config.setAllowedOrigins(List.of("https://showping-live.com"));
                    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN", "X-Requested-With"));
                    config.setAllowCredentials(true);
                    return config;
                }))
                // CSRF: 쿠키 기반 토큰 사용, 특정 엔드포인트는 제외
                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepo)
                        .csrfTokenRequestHandler(csrfAttrHandler)
                        .ignoringRequestMatchers(
                                "/auth/mfa/**"
                        )
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 인가 규칙
                .authorizeHttpRequests(auth -> auth
                        // permitAll
                        .requestMatchers(
                                "/", "/login","/login/**","/error","/error/**","/error-page/**",
                                "/webrtc/watch", "/webrtc/watch/**", "/css/**", "/js/**", "/images/**",
                                "/img/**", "/assets/**", "/oauth/**", "/api/register", "/api/auth/login", "/api/auth/logout",
                                "/api/admin/login", "/product/detail/**", "/api/categories/**", "/category/**",
                                "/api/products/**", "/api/admin/verify-totp", "/login/signup/**", "/api/member/verify-code", "/api/auth/refresh-token-check/**", "/stream/broadcast", "/stream/vod/list/page/**",
                                "/favicon.ico", "/api/auth/**", "/api/member/check-duplicate", "/api/member/register",
                                "/api/member/send-code/**", "/api/member/check-email-duplicate", "/api/member/check-phone-duplicate",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html","/api/hls/**",
                                "/api/live/standby", "/api/live/product/list", "/api/live/onair",
                                "/api/live/live-info", "/api/live/active", "/stream/watch/**", "/stream/list/**",
                                "/watch/vod/**", "/api/watch/insert", "/product/product_list","/product/product_list/**",
                                "/product/product_detail/**","/record", "/live", "/api/csrf", "/api/live/register", "/api/vod/list/**", "/api/vod/subtitle/**"
                                ,"/api/auth/reissue","/auth/mfa/**"
                                // ⚠️ 성능 테스트 전용 인증 우회 - 테스트 완료 후 반드시 제거
                                ,"/api/admin/members", "/api/admin/members/**"
                        ).permitAll()
                        // ADMIN
                        .requestMatchers(
                                "/api/admin/**",
                                "/admin/**","/api/live/stop", "/api/live/start",
                                "/api/report/updateStatus", "/api/report/register","/api/batch/**",
                                "/api/report/report", "/api/chatRoom/create",
                                "/api/vod/upload", "/api/vod/subtitle/**",
                                "/stream/stream", "/report/**", "/api/report/list"
                        ).access((authCtx, reqCtx) -> mfaGuard.decision(authCtx.get()))
                        // USER
                        .requestMatchers(
                                "/user/**","/api/carts/**", "/api/payments/**", "/api/orders/**",
                                "/api/watch/history/**", "/api/payments/verify",
                                "/api/payments/complete", "/api/chat/**",
                                "/watch/history/**","/api/watch/history/list","/api/watch/history/list/**",
                                "/cart/**", "/product/product_cart", "/payment/**",
                                "/product/product_payment", "/success/**", "/payment/success/**","/api/auth/user-info"
                        ).hasAnyRole("USER", "ADMIN")
                        // 그 외
                        .anyRequest().authenticated()
                )
                // 로그인, 로그아웃 disable (JWT)
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                // 예외 처리
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(customAuthenticationEntryPoint)
                        .accessDeniedHandler(customAccessDeniedHandler)
                )
                // CSRF 쿠키 필터 + JWT 필터
                .addFilterAfter(new CsrfInitFilter(), CsrfFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .addFilterAfter(new CsrfDebugFilter(), CsrfFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        log.info("securityConfig 적용 완료");
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SpringSecurityDialect springSecurityDialect() {
        return new SpringSecurityDialect();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> response.sendRedirect("/");
    }

    @Bean
    public LogoutSuccessHandler logoutSuccessHandler() {
        return (request, response, authentication) -> response.sendRedirect("/login");
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("http://localhost:8080", "https://showping-live.com")
                        .allowedMethods("*")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}
