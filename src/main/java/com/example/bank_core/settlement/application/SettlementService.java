package com.example.bank_core.settlement.application;

// [무엇] 일일 정산 서비스
// [역할] 특정 날짜의 완료된 입금·이체를 합계 내고, 원장 검사 결과와 함께 daily_settlement에 한 줄 저장
// [누가 쓰나] SettlementScheduler(매일 자동 실행), SettlementController(운영자 수동 실행), 정산 테스트

import com.example.bank_core.common.exception.BusinessException;
import com.example.bank_core.common.exception.ErrorCode;
import com.example.bank_core.ledger.application.LedgerVerificationService;
import com.example.bank_core.settlement.domain.DailySettlement;
import com.example.bank_core.settlement.domain.DailySettlement.LedgerCheck;
import com.example.bank_core.settlement.domain.DailySettlementRepository;
import com.example.bank_core.transfer.domain.TransferRepository;
import com.example.bank_core.transfer.domain.TransferRepository.TypeSummaryRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final DailySettlementRepository dailySettlementRepository;
    private final TransferRepository transferRepository;
    private final LedgerVerificationService ledgerVerificationService;

    /**
     * 정산 실행
     * 같은 날짜를 여러 번 실행해도 결과는 한 줄만 남음 (멱등성)
     * - 1차: 이미 정산한 날짜면 새로 계산하지 않고 기존 결과를 돌려줌
     * - 최종: DB의 UNIQUE(settlement_date) 제약이 중복 저장을 막음
     */
    @Transactional
    public DailySettlement settle(LocalDate date) {
        // 0. 끝난 날짜(어제 이전)만 정산 가능
        //    오늘을 정산하면 이후 거래가 빠진 채 저장되고, 멱등성 때문에 다시 계산되지도 않음
        if (!date.isBefore(LocalDate.now(SEOUL))) {
            throw new BusinessException(ErrorCode.SETTLEMENT_DATE_NOT_CLOSED, date.toString());
        }

        // 1. 이미 정산한 날짜인지 확인
        Optional<DailySettlement> existing = dailySettlementRepository.findBySettlementDate(date);
        if (existing.isPresent()) {
            log.info("이미 정산된 날짜라 건너뜀: {}", date);
            return existing.get();
        }

        // 2. 그날 [00:00, 다음날 00:00) 사이에 완료된 거래를 종류별로 합계
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        int depositCount = 0;
        long depositAmount = 0;
        int transferCount = 0;
        long transferAmount = 0;

        for (TypeSummaryRow row : transferRepository.summarizeCompletedBetween(start, end)) {
            switch (row.getType()) {
                case "DEPOSIT" -> {
                    depositCount = row.getTxCount().intValue();
                    depositAmount = row.getTotalAmount();
                }
                case "TRANSFER" -> {
                    transferCount = row.getTxCount().intValue();
                    transferAmount = row.getTotalAmount();
                }
                default -> log.warn("정산 대상이 아닌 거래 종류: {}", row.getType());
            }
        }

        // 3. 원장 정합성 검사 (③에서 만든 검사를 재사용. 정산 시점의 전체 원장을 검사)
        LedgerCheck ledgerCheck = ledgerVerificationService.verify().consistent()
                ? LedgerCheck.PASSED
                : LedgerCheck.FAILED;

        // 4. 결과 저장
        DailySettlement saved = dailySettlementRepository.save(DailySettlement.create(
                date, depositCount, depositAmount, transferCount, transferAmount, ledgerCheck));

        log.info("정산 완료: 날짜={}, 입금 {}건 {}원, 이체 {}건 {}원, 원장 검사={}",
                date, depositCount, depositAmount, transferCount, transferAmount, ledgerCheck);
        return saved;
    }
}