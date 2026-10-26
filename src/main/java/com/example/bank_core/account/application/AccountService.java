package com.example.bank_core.account.application;

import com.example.bank_core.account.domain.Account;
import com.example.bank_core.account.domain.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service                          // 업무 담당 직원이라고 Spring에 알려 줌
@RequiredArgsConstructor          // 창고 관리인을 자동으로 연결해 줌 (Lombok)
@Transactional(readOnly = true)   // 조회만 하니까 "읽기 전용"
public class AccountService {

    private final AccountRepository accountRepository;   // 창고 관리인

    public Account getAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "계좌를 찾을 수 없습니다: " + accountNumber));
    }
}