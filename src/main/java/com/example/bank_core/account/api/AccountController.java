package com.example.bank_core.account.api;

import com.example.bank_core.account.application.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController                         // 손님 요청을 받는 창구라고 Spring에 알려 줌
@RequestMapping("/api/v1/accounts")     // 이 창구의 주소
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;   // 업무 담당

    // GET /api/v1/accounts/100-0001 → 통장 조회
    @GetMapping("/{accountNumber}")
    public AccountResponse getAccount(@PathVariable String accountNumber) {
        return AccountResponse.from(accountService.getAccount(accountNumber));
    }
}