package com.example.bank_core.transfer.domain;

// [무엇] 이체 기록 상태
// [역할] PENDING(처리 중) → COMPLETED(완료) / FAILED(실패)
// [누가 쓰나] Transfer, TransferRepository(정산은 COMPLETED만 합계)

public enum TransferStatus {
    PENDING,     // 처리 중
    COMPLETED,   // 완료
    FAILED       // 실패
}