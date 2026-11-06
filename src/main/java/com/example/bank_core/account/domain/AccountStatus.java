package com.example.bank_core.account.domain;

// [무엇] 계좌 상태
// [역할] ACTIVE(사용 중) / FROZEN(정지) / CLOSED(해지). ACTIVE가 아니면 거래 불가(422 ACCOUNT_NOT_ACTIVE)
// [누가 쓰나] Account, TransferService

public enum AccountStatus {
    ACTIVE,   // 사용 중
    FROZEN,   // 정지
    CLOSED    // 해지
}