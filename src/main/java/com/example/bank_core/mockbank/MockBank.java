package com.example.bank_core.mockbank;

// [무엇] 모의 타행(다른 은행)의 장부와 고장 설정
// [역할] ① 입금 요청을 번호표(requestId)와 함께 기록. 같은 번호가 다시 오면 새로 입금하지 않음 (멱등성)
//        ② 계좌번호가 999로 시작하면 "없는 계좌"로 거절
//        ③ 실험용 고장 확률(실패, 지연)을 들고 있음
//        우리 은행 DB와는 완전히 따로, 메모리에만 기록 (서버를 다시 켜면 비워짐)
// [누가 쓰나] MockBankController

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MockBank {

    // 입금 처리 결과
    public enum Result {
        SUCCESS,    // 입금함
        REJECTED    // 거절함 (없는 계좌)
    }

    // 다른 은행 장부의 한 줄
    public record Deposit(String requestId, String accountNumber, long amount, Result result) {
    }

    // 장부 요약 (실험 결과 확인용)
    public record Stats(
            int successCount,           // 입금한 건수
            long successAmount,         // 입금한 총액
            int rejectedCount,          // 거절한 건수
            int duplicateRequestCount,  // 같은 번호표로 다시 온 요청 수 (다시 입금하지 않음)
            double failRate,
            double delayRate
    ) {
    }

    // 번호표 → 장부 한 줄
    private final Map<String, Deposit> deposits = new ConcurrentHashMap<>();
    private final AtomicInteger duplicateRequests = new AtomicInteger();

    // 고장 설정 (기본은 고장 없음). volatile: 여러 요청(스레드)이 항상 최신 값을 보게 함
    private volatile double failRate = 0.0;     // 처리 전에 500 오류로 끝날 확률
    private volatile double delayRate = 0.0;    // 처리는 하고 응답을 늦게 보낼 확률
    private volatile long delayMillis = 2000;   // 늦게 보낼 때 기다리는 시간 (우리 쪽 타임아웃보다 길게)

    /**
     * 입금 처리 (같은 번호표면 처음 결과를 그대로 돌려줌)
     * synchronized: 같은 번호표 두 개가 동시에 와도 한 번만 기록되게 한 줄로 세움
     */
    public synchronized Deposit deposit(String requestId, String accountNumber, long amount) {
        Deposit existing = deposits.get(requestId);
        if (existing != null) {
            duplicateRequests.incrementAndGet();
            return existing;
        }
        Result result = accountNumber.startsWith("999") ? Result.REJECTED : Result.SUCCESS;
        Deposit deposit = new Deposit(requestId, accountNumber, amount, result);
        deposits.put(requestId, deposit);
        return deposit;
    }

    // 번호표로 조회 (없으면 빈 값 = 요청을 받은 적 없음)
    public Optional<Deposit> find(String requestId) {
        return Optional.ofNullable(deposits.get(requestId));
    }

    // 이번 요청을 "실패" 고장으로 만들지 (확률)
    public boolean shouldFail() {
        return ThreadLocalRandom.current().nextDouble() < failRate;
    }

    // 이번 요청을 "지연" 고장으로 만들지 (확률)
    public boolean shouldDelay() {
        return ThreadLocalRandom.current().nextDouble() < delayRate;
    }

    public long getDelayMillis() {
        return delayMillis;
    }

    // 고장 확률 바꾸기 (실험용)
    public void configure(double failRate, double delayRate, long delayMillis) {
        this.failRate = failRate;
        this.delayRate = delayRate;
        this.delayMillis = delayMillis;
    }

    // 장부 비우기 + 고장 끄기 (실험 시작 전 초기화용)
    public synchronized void reset() {
        deposits.clear();
        duplicateRequests.set(0);
        configure(0.0, 0.0, 2000);
    }

    public Stats stats() {
        int successCount = 0;
        long successAmount = 0;
        int rejectedCount = 0;
        for (Deposit deposit : deposits.values()) {
            if (deposit.result() == Result.SUCCESS) {
                successCount++;
                successAmount += deposit.amount();
            } else {
                rejectedCount++;
            }
        }
        return new Stats(successCount, successAmount, rejectedCount, duplicateRequests.get(), failRate, delayRate);
    }
}