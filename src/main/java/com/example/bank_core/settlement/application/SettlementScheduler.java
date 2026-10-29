package com.example.bank_core.settlement.application;

// [무엇] 일일 정산 자동 실행기
// [역할] 매일 00:05(한국 시간)에 "어제" 날짜의 정산을 실행
// [누가 쓰나] Spring 스케줄러가 시간이 되면 자동으로 호출. 직접 부르는 곳은 없음

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final SettlementService settlementService;

    /**
     * cron 형식: 초 분 시 일 월 요일 → "0 5 0 * * *" = 매일 0시 5분 0초
     * 00:00이 아니라 00:05인 이유: 자정 직전에 시작한 거래가 커밋을 마칠 여유를 줌
     * zone: 서버가 어느 나라에 있든 한국 시간 기준으로 실행
     */
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    public void settleYesterday() {
        LocalDate yesterday = LocalDate.now(SEOUL).minusDays(1);
        try {
            settlementService.settle(yesterday);
        } catch (Exception e) {
            // 실패해도 서버는 계속 돌아가야 하므로 여기서 잡고 에러 로그만 남김
            // 운영자는 로그를 보고 수동 실행 API로 다시 돌릴 수 있음
            log.error("일일 정산 실패: 날짜={}", yesterday, e);
        }
    }
}