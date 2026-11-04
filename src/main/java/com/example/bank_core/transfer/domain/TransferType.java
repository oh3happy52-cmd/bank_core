package com.example.bank_core.transfer.domain;

// [무엇] 이체 종류
// [역할] transfer 테이블의 type 칸에 들어가는 값 (DB에는 글자로 저장, 최대 10글자)
// [누가 쓰나] Transfer, TransferService, InterbankTransferService, 정산

public enum TransferType {
    DEPOSIT,     // 입금 (시스템 계좌 SYS-CASH → 고객 계좌)
    WITHDRAWAL,  // 출금 (현재 사용 안 함)
    TRANSFER,    // 우리 은행 안의 이체 (고객 → 고객)
    INTERBANK,   // 타행 이체 보내기 (고객 계좌 → 보관 계좌 SYS-INTERBANK)
    REFUND       // 타행 이체 환불 (보관 계좌 SYS-INTERBANK → 고객 계좌)
}