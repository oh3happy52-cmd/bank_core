package com.example.bank_core.transfer.application;

import com.example.bank_core.TestcontainersConfiguration;
import com.example.bank_core.account.domain.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// 1주차, 비관적 락 실험과 같은 조건으로 낙관적 락을 실험
// 테스트 2개: (1) 재시도 없이 실패 건수 확인, (2) 재시도로 100건 모두 성공시키기
// 주의: 클래스에 @Transactional을 붙이면 안 됨
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransferOptimisticLockTest {

    private static final int REQUEST_COUNT = 100;   // 동시에 보낼 이체 요청 수
    private static final long AMOUNT = 1_000;       // 요청 1건당 금액

    @Autowired
    TransferService transferService;

    @Autowired
    OptimisticLockTransferFacade optimisticLockTransferFacade;

    @Autowired
    AccountRepository accountRepository;

    @Test
    void 낙관적_락_재시도_없이_동시에_100번_이체하면_일부는_실패하지만_잔액은_정확하다() throws InterruptedException {
        transferService.deposit("setup-" + UUID.randomUUID(), "100-0001", 100_000);
        long fromBefore = balanceOf("100-0001");
        long toBefore = balanceOf("100-0002");

        // 재시도 없이 한 번만 시도
        Result result = runConcurrently(() -> transferService.transferWithOptimisticLock(
                "optimistic-" + UUID.randomUUID(), "100-0001", "100-0002", AMOUNT));

        long fromAfter = balanceOf("100-0001");
        long toAfter = balanceOf("100-0002");
        long expectedFrom = fromBefore - result.success() * AMOUNT;
        long expectedTo = toBefore + result.success() * AMOUNT;

        System.out.println("========== 동시성 실험 결과 (낙관적 락, 재시도 없음) ==========");
        System.out.println("요청 수          : " + REQUEST_COUNT);
        System.out.println("성공 / 실패      : " + result.success() + " / " + result.fail());
        System.out.println("출금 계좌 기대 잔액 : " + expectedFrom + "  →  실제: " + fromAfter);
        System.out.println("입금 계좌 기대 잔액 : " + expectedTo + "  →  실제: " + toAfter);
        System.out.println("처리 시간        : " + result.elapsedMs() + " ms");
        System.out.println("============================================================");

        // 일부가 실패하더라도, "성공한 건수만큼은 정확히" 반영되어야 함 (덮어쓰기 없음)
        assertThat(result.success() + result.fail()).isEqualTo(REQUEST_COUNT);
        assertThat(fromAfter).isEqualTo(expectedFrom);
        assertThat(toAfter).isEqualTo(expectedTo);
    }

    @Test
    void 낙관적_락_재시도로_동시에_100번_이체하면_모두_성공하고_잔액이_정확하다() throws InterruptedException {
        transferService.deposit("setup-" + UUID.randomUUID(), "100-0001", 100_000);
        long fromBefore = balanceOf("100-0001");
        long toBefore = balanceOf("100-0002");
        optimisticLockTransferFacade.resetRetryCount();

        // 실패하면 재시도하는 버전으로 실행
        Result result = runConcurrently(() -> optimisticLockTransferFacade.transfer(
                "optimistic-retry-" + UUID.randomUUID(), "100-0001", "100-0002", AMOUNT));

        long fromAfter = balanceOf("100-0001");
        long toAfter = balanceOf("100-0002");
        long expectedFrom = fromBefore - REQUEST_COUNT * AMOUNT;
        long expectedTo = toBefore + REQUEST_COUNT * AMOUNT;

        System.out.println("========== 동시성 실험 결과 (낙관적 락 + 재시도) ==========");
        System.out.println("요청 수          : " + REQUEST_COUNT);
        System.out.println("성공 / 실패      : " + result.success() + " / " + result.fail());
        System.out.println("전체 재시도 횟수 : " + optimisticLockTransferFacade.getRetryCount());
        System.out.println("출금 계좌 기대 잔액 : " + expectedFrom + "  →  실제: " + fromAfter);
        System.out.println("입금 계좌 기대 잔액 : " + expectedTo + "  →  실제: " + toAfter);
        System.out.println("처리 시간        : " + result.elapsedMs() + " ms");
        System.out.println("========================================================");

        assertThat(result.success()).isEqualTo(REQUEST_COUNT);
        assertThat(fromAfter).isEqualTo(expectedFrom);
        assertThat(toAfter).isEqualTo(expectedTo);
    }

    // ── 공통: 이체 요청 100건을 동시에 실행하고 결과를 돌려줌 ──

    // 예외를 던질 수 있는 작업 하나를 나타내는 인터페이스
    interface TransferTask {
        void run() throws Exception;
    }

    // 실행 결과를 담는 상자 (성공 수, 실패 수, 걸린 시간)
    record Result(int success, int fail, long elapsedMs) {
    }

    private Result runConcurrently(TransferTask task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(32);   // 스레드 32개
        CountDownLatch startSignal = new CountDownLatch(1);            // 동시에 출발시키기 위한 신호
        CountDownLatch finished = new CountDownLatch(REQUEST_COUNT);   // 전부 끝났는지 세는 카운터
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        for (int i = 0; i < REQUEST_COUNT; i++) {
            executor.submit(() -> {
                try {
                    startSignal.await();
                    task.run();
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

        return new Result(success.get(), fail.get(), elapsedMs);
    }

    private long balanceOf(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).orElseThrow().getBalance();
    }
}