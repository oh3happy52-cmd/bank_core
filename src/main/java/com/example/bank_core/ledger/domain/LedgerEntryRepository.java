package com.example.bank_core.ledger.domain;

// [무엇] 원장(ledger_entry 테이블)을 DB에서 읽고 쓰는 창구
// [역할] 원장 저장 + 정합성 검사용 SQL 3개 (잔액-장부 비교, 전체 잔액 합계, 이체별 장부 균형)
// [누가 쓰나] TransferService(원장 기록), LedgerVerificationService(정합성 검사)

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    // 검사 1의 결과 한 줄을 담는 틀. SQL의 AS 이름과 getter 이름이 같아야 자동으로 채워짐
    interface AccountMismatchRow {
        String getAccountNumber();   // 계좌번호
        Long getBalance();           // account 테이블의 잔액
        Long getLedgerBalance();     // 장부로 계산한 잔액 (들어온 돈 - 나간 돈)
    }

    /**
     * 검사 1: 잔액과 장부 합계가 다른 계좌 찾기
     * - LEFT JOIN: 거래가 한 번도 없는 계좌도 포함 (장부 합계 0으로 계산)
     * - CASE: CREDIT(들어옴)은 +, DEBIT(나감)은 -
     * - HAVING: 그룹으로 묶은 뒤 "잔액 ≠ 장부 합계"인 계좌만 남김
     * 결과가 비어 있으면 모든 계좌가 일치
     */
    @Query(value = """
            SELECT a.account_number AS accountNumber,
                   a.balance        AS balance,
                   CAST(COALESCE(SUM(CASE WHEN e.entry_type = 'CREDIT' THEN e.amount
                                          ELSE -e.amount END), 0) AS SIGNED) AS ledgerBalance
            FROM account a
            LEFT JOIN ledger_entry e ON e.account_id = a.id
            GROUP BY a.id, a.account_number, a.balance
            HAVING a.balance <> ledgerBalance
            """, nativeQuery = true)
    List<AccountMismatchRow> findAccountMismatches();

    /**
     * 검사 2: 모든 계좌 잔액의 총합
     * 시스템 계좌(SYS-CASH)가 내준 만큼 마이너스가 되므로, 정상이면 항상 0
     */
    @Query(value = "SELECT CAST(COALESCE(SUM(balance), 0) AS SIGNED) FROM account", nativeQuery = true)
    long sumAllBalances();

    /**
     * 검사 3: 장부가 맞지 않는 완료된 이체 찾기
     * 정상 이체는 장부가 정확히 2줄이고, 나간 돈(DEBIT) = 들어온 돈(CREDIT)
     * - LEFT JOIN: 장부가 아예 없는 이체도 잡아냄 (COUNT가 0)
     * 결과가 비어 있으면 모든 이체가 정상
     */
    @Query(value = """
            SELECT t.id
            FROM transfer t
            LEFT JOIN ledger_entry e ON e.transfer_id = t.id
            WHERE t.status = 'COMPLETED'
            GROUP BY t.id
            HAVING COUNT(e.id) <> 2
                OR SUM(CASE WHEN e.entry_type = 'DEBIT'  THEN e.amount ELSE 0 END)
                <> SUM(CASE WHEN e.entry_type = 'CREDIT' THEN e.amount ELSE 0 END)
            """, nativeQuery = true)
    List<Long> findUnbalancedTransferIds();
}