package com.example.bank_core.ledger.application;

// [무엇] 원장 정합성 검사 서비스
// [역할] 검사 SQL 3개를 실행하고, 하나라도 어긋나면 consistent=false + 경고 로그
// [누가 쓰나] LedgerVerificationController(API), 정합성 테스트, (다음 단계) 일일 정산 배치

import com.example.bank_core.ledger.application.LedgerVerificationResult.AccountMismatch;
import com.example.bank_core.ledger.domain.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerVerificationService {

    private final LedgerEntryRepository ledgerEntryRepository;

    /**
     * 정합성 검사 실행
     * readOnly 트랜잭션 하나로 3개 SQL을 실행 → MySQL 기본 격리 수준(REPEATABLE READ)에서는
     * 3개 SQL이 "같은 시점의 데이터"를 보므로, 검사 도중 이체가 들어와도 결과가 섞이지 않음
     */
    @Transactional(readOnly = true)
    public LedgerVerificationResult verify() {
        // 검사 1: 잔액과 장부 합계가 다른 계좌
        List<AccountMismatch> mismatchedAccounts = ledgerEntryRepository.findAccountMismatches().stream()
                .map(row -> new AccountMismatch(row.getAccountNumber(), row.getBalance(), row.getLedgerBalance()))
                .toList();

        // 검사 2: 전체 잔액 합계 (정상이면 0)
        long totalBalance = ledgerEntryRepository.sumAllBalances();

        // 검사 3: 장부가 안 맞는 이체
        List<Long> unbalancedTransferIds = ledgerEntryRepository.findUnbalancedTransferIds();

        boolean consistent = mismatchedAccounts.isEmpty()
                && totalBalance == 0
                && unbalancedTransferIds.isEmpty();

        if (consistent) {
            log.info("원장 정합성 검사 통과");
        } else {
            log.warn("원장 정합성 불일치 발견: 잔액 불일치 계좌={}, 전체 잔액 합계={}, 장부 불균형 이체={}",
                    mismatchedAccounts, totalBalance, unbalancedTransferIds);
        }

        return new LedgerVerificationResult(
                consistent, totalBalance, mismatchedAccounts, unbalancedTransferIds, LocalDateTime.now());
    }
}