package com.example.bank_core.ledger.application;

// [무엇] 원장 정합성 검사 결과
// [역할] 검사 3가지의 결과를 한 번에 담아서 돌려줌 (API 응답으로도 그대로 사용)
// [누가 쓰나] LedgerVerificationService가 만들고, LedgerVerificationController가 응답으로 보냄

import java.time.LocalDateTime;
import java.util.List;

public record LedgerVerificationResult(
        boolean consistent,                      // 3가지 검사가 모두 통과했으면 true
        long totalBalance,                       // 검사 2: 전체 잔액 합계 (정상이면 0)
        List<AccountMismatch> mismatchedAccounts, // 검사 1: 잔액 ≠ 장부 합계인 계좌들 (정상이면 빈 목록)
        List<Long> unbalancedTransferIds,         // 검사 3: 장부가 안 맞는 이체 번호들 (정상이면 빈 목록)
        LocalDateTime verifiedAt                  // 검사한 시각
) {
    // 잔액이 안 맞는 계좌 한 건
    public record AccountMismatch(
            String accountNumber,
            long balance,        // account 테이블의 잔액
            long ledgerBalance   // 장부로 계산한 잔액
    ) {
    }
}