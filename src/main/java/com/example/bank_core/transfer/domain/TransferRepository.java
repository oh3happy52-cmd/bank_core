package com.example.bank_core.transfer.domain;

// [무엇] 이체 기록(transfer 테이블)을 DB에서 읽고 쓰는 창구
// [역할] 저장, 조회 + Idempotency-Key로 기존 이체 찾기 + 기간별 거래 합계(정산용)
// [누가 쓰나] TransferService, SettlementService

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    // 같은 Idempotency-Key로 이미 처리한 이체가 있는지 찾기 (멱등성 검사용)
    // 메서드 이름만 보고 Spring이 "where idempotency_key = ?" 쿼리를 자동으로 만들어 줌
    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    // 정산 SQL 결과 한 줄을 담는 틀 (거래 종류 하나당 한 줄). SQL의 AS 이름과 getter 이름이 같아야 함
    interface TypeSummaryRow {
        String getType();          // DEPOSIT / TRANSFER
        Long getTxCount();         // 건수
        Long getTotalAmount();     // 합계 금액
    }

    /**
     * 기간 안에 "완료된" 거래를 종류별로 묶어서 건수와 합계 구하기 (일일 정산용)
     * - start 이상, end 미만: 하루 = [그날 00:00, 다음날 00:00)
     *   (<= 23:59:59 로 쓰면 23:59:59.5 같은 시각이 빠질 수 있어서 "다음날 00:00 미만"으로 씀)
     * - 실패/처리 중 거래는 돈이 실제로 움직이지 않았으므로 제외
     */
    @Query(value = """
            SELECT type                                     AS type,
                   COUNT(*)                                 AS txCount,
                   CAST(COALESCE(SUM(amount), 0) AS SIGNED) AS totalAmount
            FROM transfer
            WHERE status = 'COMPLETED'
              AND completed_at >= :start
              AND completed_at <  :end
            GROUP BY type
            """, nativeQuery = true)
    List<TypeSummaryRow> summarizeCompletedBetween(@Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end);
}