package com.example.bank_core.account.api;

// [무엇] 계좌 조회 API 창구
// [역할] 계좌번호를 받아 계좌 정보(소유자, 상태, 잔액)를 돌려줌
// [누가 쓰나] 외부 클라이언트(앱, curl, Swagger)

import com.example.bank_core.account.application.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "계좌", description = "계좌 조회")
@RestController                         // 외부 요청을 받는 창구라고 Spring에 알려 줌
@RequestMapping("/api/v1/accounts")     // 이 창구의 주소
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;   // 업무 담당

    // GET /api/v1/accounts/100-0001 → 계좌 조회
    @Operation(summary = "계좌 조회", description = "계좌번호로 소유자, 종류, 상태, 잔액을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "ACCOUNT_NOT_FOUND")
    @GetMapping("/{accountNumber}")
    public AccountResponse getAccount(
            @Parameter(description = "조회할 계좌번호", example = "100-0001")
            @PathVariable String accountNumber) {
        return AccountResponse.from(accountService.getAccount(accountNumber));
    }
}