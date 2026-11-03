package com.example.bank_core.account.application;

/**
 * [무엇] 계좌 조회 업무를 담당하는 서비스
 * [역할] 계좌번호로 계좌를 찾고, 없으면 ACCOUNT_NOT_FOUND(404)
 *        요청한 회원이 계좌 주인도 관리자도 아니면 FORBIDDEN(403)
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

    // 계좌 조회. 없으면 404, 주인도 관리자도 아니면 403
    public Account getAccount(String accountNumber, LoginMember loginMember) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, accountNumber));

        if (!loginMember.isAdmin() && !account.isOwnedBy(loginMember.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return account;
    }
}