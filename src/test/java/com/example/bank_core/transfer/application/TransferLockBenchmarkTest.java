package com.example.bank_core.transfer.application;

import com.example.bank_core.TestcontainersConfiguration;
import com.example.bank_core.account.domain.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [2주차] 세 가지 동시성 제어 방식을 같은 조건에서 반복 측정해 비교
 * - 비교 대상: 비관적 락 / 낙관적 락 + 재시도 / 원자적 UPDATE
 * - 공정성을 위해:
 *   1) 측정 전에 각 방식을 1번씩 워밍업 실행하고 결과는 버림 (JVM이 처음 실행할 때 느린 영향 제거)
 *   2) 각 방식을 5번씩 측정해서 평균, 최소, 최대를 계산
 *   3) 회차마다 실행 순서를 돌려서, 특정 방식이 항상 먼저/나중에 실행되는 영향을 줄임
 *   4) 한 번의 실행, 같은 테스트 DB에서 모두 측정
 * 주의: 클래스에 @Transactional을 붙이면 안 됨
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransferLockBenchmarkTest {

    private static final String FROM = "100-0001";    // 출금 계좌
    private static final String TO = "100-0002";      // 입금 계좌
    private static final int REQUEST_COUNT = 100;     // 회차당 동시에 보낼 이체 요청 수
    private static final long AMOUNT = 1_000;         // 요청 1건당 금액
    private static final int WARMUP_ROUNDS = 1;       // 워밍업 회차 수 (결과에서 제외)
    private static final int MEASURE_ROUNDS = 5;      // 측정 회차 수

    @Autowired
    TransferService transferService;

    @Autowired
    OptimisticLockTransferFacade optimisticLockTransferFacade;

    @Autowired
    AccountRepository accountRepository;

    // 이체 1건을 실행하는 작업 (요청마다 다른 멱등성 키를 받음)
    interface TransferTask {
        void run(String idempotencyKey) throws Exception;
    }

    // 비교할 방식: 이름 + 이체 실행 방법
    record Method(String name, TransferTask task) {
    }

    // 한 회차 실행 결과: 성공 수, 실패 수, 걸린 시간
    record RoundResult(int success, int fail, long elapsedMs) {
    }

    @Test
    void 세_가지_방식을_같은_조건에서_반복_측정한다() throws InterruptedException {
        List<Method> methods = List.of(
                new Method("비관적 락", key ->
                        transferService.transferWithPessimisticLock(key, FROM, TO, AMOUNT)),
                new Method("낙관적 락+재시도", key ->
                        optimisticLockTransferFacade.transfer(key, FROM, TO, AMOUNT)),
                new Method("원자적 UPDATE", key ->
                        transferService.transferWithAtomicUpdate(key, FROM, TO, AMOUNT))
        );

        // 방식별 측정 시간과 재시도 횟수를 모아 둘 곳 (LinkedHashMap: 넣은 순서대로 출력됨)
        Map<String, List<Long>> elapsedTimes = new LinkedHashMap<>();
        Map<String, List<Long>> retryCounts = new LinkedHashMap<>();
        for (Method method : methods) {
            elapsedTimes.put(method.name(), new ArrayList<>());
            retryCounts.put(method.name(), new ArrayList<>());
        }

        System.out.println("========== 동시성 제어 방식 반복 측정 ==========");

        for (int round = 1; round <= WARMUP_ROUNDS + MEASURE_ROUNDS; round++) {
            boolean warmup = round <= WARMUP_ROUNDS;

            // 회차마다 실행 순서를 한 칸씩 돌림 (예: 비관→낙관→원자, 원자→비관→낙관, ...)
            List<Method> order = new ArrayList<>(methods);
            Collections.rotate(order, round);

            for (Method method : order) {
                // 준비: 출금 계좌에 이번 회차에 쓸 금액만큼 입금
                transferService.deposit("setup-" + UUID.randomUUID(), FROM, REQUEST_COUNT * AMOUNT);
                long fromStart = balanceOf(FROM);
                long toStart = balanceOf(TO);
                optimisticLockTransferFacade.resetRetryCount();

                RoundResult result = runConcurrently(method.task());

                long fromEnd = balanceOf(FROM);
                long toEnd = balanceOf(TO);
                long fromExpected = fromStart - REQUEST_COUNT * AMOUNT;
                long toExpected = toStart + REQUEST_COUNT * AMOUNT;
                long retries = optimisticLockTransferFacade.getRetryCount();   // 낙관적 락에서만 의미 있음

                System.out.printf("[%s] %s | 성공 %d / 실패 %d | 출금 계좌 시작 %,d → 기대 %,d / 실제 %,d | %,d ms%n",
                        warmup ? "워밍업" : round - WARMUP_ROUNDS + "회차",
                        method.name(), result.success(), result.fail(),
                        fromStart, fromExpected, fromEnd, result.elapsedMs());

                // 모든 회차에서 정확성 검증: 100건 모두 성공 + 잔액 정확
                assertThat(result.success()).isEqualTo(REQUEST_COUNT);
                assertThat(fromEnd).isEqualTo(fromExpected);
                assertThat(toEnd).isEqualTo(toExpected);

                if (!warmup) {
                    elapsedTimes.get(method.name()).add(result.elapsedMs());
                    retryCounts.get(method.name()).add(retries);
                }
            }
        }

        // 요약 출력
        System.out.println();
        System.out.println("========== 요약 (워밍업 제외, " + MEASURE_ROUNDS + "회 측정) ==========");
        for (Method method : methods) {
            List<Long> times = elapsedTimes.get(method.name());
            long avg = (long) times.stream().mapToLong(Long::longValue).average().orElse(0);
            long min = times.stream().mapToLong(Long::longValue).min().orElse(0);
            long max = times.stream().mapToLong(Long::longValue).max().orElse(0);
            long avgRetry = (long) retryCounts.get(method.name()).stream()
                    .mapToLong(Long::longValue).average().orElse(0);

            System.out.printf("%-12s | 평균 %,6d ms | 최소 %,6d ms | 최대 %,6d ms | 평균 재시도 %,d회 | 측정값 %s%n",
                    method.name(), avg, min, max, avgRetry, times);
        }
        System.out.println("=================================================");
    }

    // 이체 요청 100건을 동시에 실행하고 결과를 돌려줌
    private RoundResult runConcurrently(TransferTask task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(32);   // 스레드 32개
        CountDownLatch startSignal = new CountDownLatch(1);            // 동시에 출발시키기 위한 신호
        CountDownLatch finished = new CountDownLatch(REQUEST_COUNT);   // 전부 끝났는지 세는 카운터
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        for (int i = 0; i < REQUEST_COUNT; i++) {
            executor.submit(() -> {
                try {
                    startSignal.await();
                    task.run("bench-" + UUID.randomUUID());   // 요청마다 다른 멱등성 키
                    success.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    finished.countDown();
                }
            });
        }

        long startTime = System.nanoTime();
        startSignal.countDown();   // 100건 동시에 출발
        finished.await();          // 모두 끝날 때까지 대기
        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        executor.shutdown();

        return new RoundResult(success.get(), fail.get(), elapsedMs);
    }

    private long balanceOf(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).orElseThrow().getBalance();
    }
}