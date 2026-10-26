package com.example.bank_core.account.api;

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