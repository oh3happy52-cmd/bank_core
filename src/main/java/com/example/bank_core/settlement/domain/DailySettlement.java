package com.example.bank_core.settlement.domain;

// [무엇] 일일 정산 결과 엔티티 (daily_settlement 테이블 한 줄)
// [역할] 하루치 입금·이체 건수와 합계, 원장 검사 결과를 기록. 한 번 저장하면 수정하지 않음
// [누가 쓰나] SettlementService가 만들어 저장, DailySettlementRepository가 DB와 연결

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_settlement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailySettlement {

    // 원장 정합성 검사 결과
    public enum LedgerCheck {
        PASSED,   // 통과
        FAILED    // 불일치 발견
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_date", nullable = false, unique = true)
    private LocalDate settlementDate;

    @Column(name = "deposit_count", nullable = false)
    private int depositCount;

    @Column(name = "deposit_amount", nullable = false)
    private long depositAmount;

    @Column(name = "transfer_count", nullable = false)
    private int transferCount;

    @Column(name = "transfer_amount", nullable = false)
    private long transferAmount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "ledger_check", nullable = false, length = 10)
    private LedgerCheck ledgerCheck;

    @Column(name = "settled_at", nullable = false)
    private LocalDateTime settledAt;

    // 정산 결과 한 줄 만들기
    public static DailySettlement create(LocalDate settlementDate,
                                         int depositCount, long depositAmount,
                                         int transferCount, long transferAmount,
                                         LedgerCheck ledgerCheck) {
        DailySettlement settlement = new DailySettlement();
        settlement.settlementDate = settlementDate;
        settlement.depositCount = depositCount;
        settlement.depositAmount = depositAmount;
        settlement.transferCount = transferCount;
        settlement.transferAmount = transferAmount;
        settlement.ledgerCheck = ledgerCheck;
        settlement.settledAt = LocalDateTime.now();
        return settlement;
    }
}