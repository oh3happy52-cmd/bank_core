package com.example.bank_core.ledger.domain;

// [무엇] 원장 한 줄 (ledger_entry 테이블의 한 줄)
// [역할] 돈이 움직일 때마다 "어느 계좌에서 얼마 나감(DEBIT)/들어옴(CREDIT), 그 뒤 잔액"을 기록 (이체 1건 = 2줄, 복식부기)
//        잔액과 원장을 비교해서 돈이 새거나 생겼는지 검사할 수 있게 함
// [누가 쓰나] TransferService(기록), LedgerVerificationService(검사)

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "ledger_entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_id", nullable = false)
    private Long transferId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "entry_type", nullable = false, length = 6)
    private EntryType entryType;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 가계부 한 줄 쓰기 (한 번 쓰면 수정 불가, 그래서 수정 기능이 없음)
    public static LedgerEntry record(Long transferId, Long accountId, EntryType entryType,
                                     long amount, long balanceAfter) {
        LedgerEntry entry = new LedgerEntry();
        entry.transferId = transferId;
        entry.accountId = accountId;
        entry.entryType = entryType;
        entry.amount = amount;
        entry.balanceAfter = balanceAfter;
        entry.createdAt = LocalDateTime.now();
        return entry;
    }
}