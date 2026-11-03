package com.example.bank_core.common.config;

// [무엇] Spring Security 설정 (문지기의 규칙표)
// [역할] ① 어떤 주소를 누가 쓸 수 있는지 정함
//        ② JWT를 만들고(encoder) 검사하는(decoder) 도구를 준비
//        ③ 비밀번호 비교 도구(BCrypt)를 준비
// [누가 쓰나] Spring Security가 모든 요청마다 자동으로 사용. 토큰 발급은 AuthService가 JwtEncoder를 씀

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // application.properties의 jwt.secret 값을 가져옴
    @Value("${jwt.secret}")
    private String jwtSecret;

    /**
     * 문지기 규칙표
     * - 로그인, Swagger 문서: 누구나
     * - /api/v1/admin/**: ADMIN 역할만 (아니면 403)
     * - 그 밖의 모든 API: 토큰이 있어야 함 (없으면 401)
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF 보호 끔: 브라우저 쿠키 로그인을 노리는 공격 방어용인데, 우리는 쿠키 대신 토큰을 헤더로 보내서 필요 없음
                .csrf(csrf -> csrf.disable())
                // 세션 안 씀: 서버가 로그인 상태를 기억하지 않고, 요청마다 토큰으로 확인 (STATELESS)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                // 요청 헤더의 "Authorization: Bearer 토큰"을 JWT로 검사
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    // 토큰 서명·검사에 쓰는 비밀 키 (서버만 앎)
    private SecretKey secretKey() {
        return new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    // 토큰 만드는 도구 (로그인 성공 시 AuthService가 사용)
    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey()));
    }

    // 토큰 검사하는 도구: 서명이 우리 비밀 키로 만든 게 맞는지, 유효 시간이 지나지 않았는지 확인
    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    // 비밀번호 비교 도구 (입력한 비밀번호와 DB의 BCrypt 해시가 맞는지 확인)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 토큰 안의 "role" 값(USER/ADMIN)을 Spring Security가 이해하는 권한(ROLE_USER/ROLE_ADMIN)으로 바꿈
    // → hasRole("ADMIN") 규칙이 이 값을 보고 판단
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}