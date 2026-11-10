package com.example.bank_core.account.api;

// [무엇] 계좌 조회 API 창구
// [역할] 계좌번호와 요청한 회원(토큰에서 꺼냄)을 받아 계좌 정보(소유자, 상태, 잔액)를 돌려줌
// [누가 쓰나] 외부 클라이언트(앱, curl, Swagger). 토큰 필요

import com.example.bank_core.account.application.AccountService;
import com.example.bank_core.common.security.LoginMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "계좌", description = "계좌 조회 (본인 계좌만, 관리자는 전체)")
@RestController                         // 외부 요청을 받는 창구라고 Spring에 알려 줌
@RequestMapping("/api/v1/accounts")     // 이 창구의 주소
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;   // 업무 담당

    // GET /api/v1/accounts/100-0001 → 계좌 조회
    @Operation(summary = "계좌 조회", description = "계좌번호로 소유자, 종류, 상태, 잔액을 조회합니다. 본인 계좌만 조회할 수 있습니다 (관리자는 전체).")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: 토큰 없음 또는 유효하지 않음")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN: 다른 사람의 계좌 또는 없는 계좌 (계좌 존재 여부를 알려 주지 않음)")
    @ApiResponse(responseCode = "404", description = "ACCOUNT_NOT_FOUND (관리자가 없는 계좌를 조회한 경우만)")
    @GetMapping("/{accountNumber}")
    public AccountResponse getAccount(
            @Parameter(description = "조회할 계좌번호", example = "100-0001")
            @PathVariable String accountNumber,
            // @AuthenticationPrincipal: 문지기가 검사를 마친 토큰을 그대로 받음
            // hidden = true: Swagger 화면에 입력 칸으로 보이지 않게 (헤더의 토큰에서 자동으로 채워지는 값이라서)
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return AccountResponse.from(accountService.getAccount(accountNumber, LoginMember.from(jwt)));
    }
}