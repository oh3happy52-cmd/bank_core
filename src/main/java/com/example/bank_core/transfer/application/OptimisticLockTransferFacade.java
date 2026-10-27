package com.example.bank_core.transfer.application;

import com.example.bank_core.transfer.domain.Transfer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * [2주차] 낙관적 락 이체 + 재시도
 * 충돌(OptimisticLockingFailureException)이 나면 잠깐 쉬었다가 새 트랜잭션으로 다시 시도함
 *
 * 왜 TransferService와 다른 클래스인가?
 * → Spring의 @Transactional은 "다른 객체에서 들어오는 호출"에만 적용됨.
 *   같은 클래스 안에서 자기 메서드를 부르면 트랜잭션이 새로 시작되지 않아서 재시도가 의미 없어짐
 */
@Component
@RequiredArgsConstructor
public class OptimisticLockTransferFacade {

    private static final int MAX_ATTEMPTS = 100;   // 최대 시도 횟수 (무한 반복 방지)

    private final TransferService transferService;

    // 실험용: 전체 재시도 횟수를 세는 숫자 (여러 스레드가 동시에 더해도 안전한 숫자)
    private final AtomicLong retryCount = new AtomicLong();

    public Transfer transfer(String idempotencyKey, String fromAccountNumber,
                             String toAccountNumber, long amount) {
        for (int attempt = 1; ; attempt++) {
            try {
                // 매번 새 트랜잭션으로 실행됨 (다른 클래스의 @Transactional 메서드를 호출하므로)
                return transferService.transferWithOptimisticLock(
                        idempotencyKey, fromAccountNumber, toAccountNumber, amount);
            } catch (OptimisticLockingFailureException e) {
                // 버전 충돌: 그사이 다른 트랜잭션이 먼저 수정함 → 이번 시도는 이미 롤백됨
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;   // 너무 많이 실패하면 포기하고 예외를 그대로 던짐
                }
                retryCount.incrementAndGet();
                sleepRandomly();   // 모두 동시에 다시 몰리지 않도록 10~50ms 랜덤으로 쉼
            }
        }
    }

    public long getRetryCount() {
        return retryCount.get();
    }

    public void resetRetryCount() {
        retryCount.set(0);
    }

    private void sleepRandomly() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(10, 51));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // 중단 신호를 다시 표시해 둠
            throw new IllegalStateException("재시도 대기 중 중단되었습니다", e);
        }
    }
}