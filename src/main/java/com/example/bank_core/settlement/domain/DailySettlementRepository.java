package com.example.bank_core.settlement.domain;

// [무엇] 일일 정산 결과(daily_settlement 테이블)를 DB에서 읽고 쓰는 창구
// [역할] 저장 + 날짜로 정산 결과 찾기 (이미 정산한 날짜인지 확인할 때 사용)
// [누가 쓰나] SettlementService

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailySettlementRepository extends JpaRepository<DailySettlement, Long> {

    // 해당 날짜의 정산 결과 찾기 → "where settlement_date = ?" 쿼리를 Spring이 자동 생성
    Optional<DailySettlement> findBySettlementDate(LocalDate settlementDate);
}