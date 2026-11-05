package com.example.bank_core.transfer.application;

// [무엇] 번호표 메모 정리 작업 (자동 실행)
// [역할] idempotency.cleanup-cron(기본: 매시 정각)마다 유효기간 지난 번호표 메모를 지움
//        유효기간 판단 자체는 요청이 올 때 TransferService가 정확히 하므로,
//        이 작업은 "메모장 크기 관리"만 맡음 (편의점 직원이 정해진 시간마다 유통기한 지난 물건을 치우는 것과 같음)
// [누가 쓰나] Spring 스케줄러가 자동으로 호출 (SchedulingConfig에서 켜 둔 기능). 직접 부르는 곳은 없음

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyKeyCleanupScheduler {

    private final IdempotencyKeyCleanupService idempotencyKeyCleanupService;

    // cron: "초 분 시 일 월 요일". 기본값 "0 0 * * * *" = 매시 0분 0초
    @Scheduled(cron = "${idempotency.cleanup-cron:0 0 * * * *}")
    public void cleanup() {
        try {
            idempotencyKeyCleanupService.deleteExpired();
        } catch (Exception e) {
            // 실패해도 서버는 계속 돌아야 하므로 잡고 로그만 남김. 다음 실행 때 다시 시도
            log.error("번호표 메모 정리 실패", e);
        }
    }
}
