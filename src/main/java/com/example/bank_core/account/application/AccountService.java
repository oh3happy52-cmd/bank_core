package com.example.bank_core.account.application;

/**
 * [무엇] 계좌 조회 업무를 담당하는 서비스
 * [역할] 계좌번호로 계좌를 찾고, 없으면 ACCOUNT_NOT_FOUND(404) 오류를 던짐
 * [누가 쓰나] AccountController (GET /api/v1/accounts/{accountNumber})
 */

import com.example.bank_core.account.domain.Account;
import com.example.bank_core.account.domain.AccountRepository;
import com.example.bank_core.common.exception.BusinessException;
import com.example.bank_core.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)   // 조회만 하니까 "읽기 전용"
public class AccountService {

    private final AccountRepository accountRepository;

    // 계좌번호로 조회. 없으면 ACCOUNT_NOT_FOUND(404)
    public Account getAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, accountNumber));
    }
}