package com.example.bank_core.member.api;

// [무엇] 로그인 API 창구
// [역할] 아이디·비밀번호를 받아 AuthService에 넘기고, 발급된 토큰을 돌려줌
// [누가 쓰나] 외부 클라이언트(앱, curl, Swagger). 토큰 없이 호출 가능 (SecurityConfig에서 permitAll)

import com.example.bank_core.member.application.AuthService;
import com.example.bank_core.member.application.AuthService.LoginResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "로그인 후 받은 토큰을 다른 API의 Authorization 헤더에 붙여서 사용")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 로그인 요청 모양
    public record LoginRequest(
            @NotBlank String loginId,    // 비어 있으면 400
            @NotBlank String password
    ) {
    }

    // 로그인 응답 모양
    public record LoginResponse(
            String accessToken,   // JWT
            String tokenType,     // 항상 "Bearer" (헤더에 "Bearer 토큰" 형태로 붙이라는 뜻)
            long expiresIn        // 유효 시간 (초)
    ) {
    }

    /**
     * 로그인
     * POST /api/v1/auth/login
     */
    @Operation(summary = "로그인", description = "아이디·비밀번호가 맞으면 JWT를 발급합니다. (개발용 계정: chulsoo / chulsoo1234)")
    @ApiResponse(responseCode = "200", description = "로그인 성공")
    @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS: 아이디 또는 비밀번호가 틀림")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = authService.login(request.loginId(), request.password());
        return new LoginResponse(result.accessToken(), "Bearer", result.expiresInSeconds());
    }
}