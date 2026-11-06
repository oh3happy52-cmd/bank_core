package com.example.bank_core.account.api;

// [무엇] 계좌 조회 응답 본문(JSON)
// [역할] 계좌(Account)에서 손님에게 보여 줄 값(계좌번호, 이름, 종류, 상태, 잔액)만 골라 담음 (내부 id, 버전은 숨김)
// [누가 쓰나] AccountController

import com.example.bank_core.account.domain.Account;

// 손님에게 보여 줄 내용만 골라 담은 안내문
public record AccountResponse(
        String accountNumber,
        String ownerName,
        String accountType,
        String status,
        long balance
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getAccountNumber(),
                account.getOwnerName(),
                account.getAccountType().name(),
                account.getStatus().name(),
                account.getBalance()
        );
    }
}