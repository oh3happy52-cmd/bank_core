package com.example.bank_core.account.domain;

// [무엇] 계좌 종류
// [역할] CUSTOMER(고객 통장) / SYSTEM(은행 내부 계좌: 입금용 SYS-CASH, 타행 보관용 SYS-INTERBANK)
// [누가 쓰나] Account, LedgerVerificationService(전체 잔액 합계 = 0 검사)

public enum AccountType {
    CUSTOMER,   // 고객 통장
    SYSTEM      // 은행 금고 통장 (입금/출금의 상대편)
}

