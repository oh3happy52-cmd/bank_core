package com.example.bank_core.account.application;

/**
 * [무엇] 계좌 조회 업무를 담당하는 서비스
 * [역할] 일반 회원: 본인 계좌가 아니면 FORBIDDEN(403). 없는 계좌번호도 403
 *          → 남의 계좌번호가 "존재하는지"조차 알려 주지 않음 (404/403 차이로 계좌번호를 알아내는 것 방지)
 *        관리자: 모든 계좌 조회 가능, 없으면 ACCOUNT_NOT_FOUND(404)
 * [누가 쓰나] AccountController (GET /api/v1/accounts/{accountNumber})
 */

import com.example.bank_core.account.domain.Account;
import com.example.bank_core.account.domain.AccountRepository;
import com.example.bank_core.common.exception.BusinessException;
import com.example.bank_core.common.exception.ErrorCode;
import com.example.bank_core.common.security.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)   // 조회만 하니까 "읽기 전용"
public class AccountService {

    private final AccountRepository accountRepository;

    // 계좌 조회. 일반 회원은 본인 계좌만(없거나 남의 것이면 403), 관리자는 전부(없으면 404)
    public Account getAccount(String accountNumber, LoginMember loginMember) {
        if (!loginMember.isAdmin()
                && !accountRepository.existsByAccountNumberAndMemberId(accountNumber, loginMember.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, accountNumber));
    }
}