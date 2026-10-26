package com.example.bank_core.ledger.domain;

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