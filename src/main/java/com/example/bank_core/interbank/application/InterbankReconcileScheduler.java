package com.example.bank_core.interbank.application;

// [무엇] 타행 이체 자동 확인 작업
// [역할] 이전 실행이 끝나고 interbank.reconcile-delay-millis(5초) 뒤에 다시 실행
//        → UNKNOWN(과 오래된 REQUESTED) 타행 이체를 다른 은행에 다시 물어봐서 완료 또는 환불로 확정
// [누가 쓰나] Spring 스케줄러가 자동으로 호출 (SchedulingConfig에서 켜 둔 기능). 직접 부르는 곳은 없음

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InterbankReconcileScheduler {

    private final InterbankTransferService interbankTransferService;

    // fixedDelay: "이전 실행이 끝난 시점부터" 정해진 시간 뒤에 다시 실행 → 실행이 겹치지 않음
    @Scheduled(fixedDelayString = "${interbank.reconcile-delay-millis}")
    public void reconcile() {
        try {
            interbankTransferService.reconcileAll();
        } catch (Exception e) {
            // 실패해도 서버는 계속 돌아야 하므로 잡고 로그만 남김. 다음 실행 때 다시 시도
            log.error("타행 자동 확인 실패", e);
        }
    }
}