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

// 1주차 실험과 똑같은 조건으로, 비관적 락 버전을 실행해서 비교
// 주의: 클래스에 @Transactional을 붙이면 안 됨 (100건이 하나의 트랜잭션으로 묶여 동시 실행이 안 됨)
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransferPessimisticLockTest {

    @Autowired
    TransferService transferService;

    @Autowired
    AccountRepository accountRepository;

    @Test
    void 비관적_락으로_동시에_100번_이체하면_잔액이_정확하다() throws InterruptedException {
        // 준비: 출금 계좌에 10만 원 입금
        transferService.deposit("setup-" + UUID.randomUUID(), "100-0001", 100_000);

        long fromBefore = balanceOf("100-0001");
        long toBefore = balanceOf("100-0002");

        int requestCount = 100;   // 동시에 보낼 이체 요청 수
        long amount = 1_000;      // 요청 1건당 금액

        // 스레드 32개로 요청을 병렬 실행
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch startSignal = new CountDownLatch(1);           // 동시에 출발시키기 위한 신호
        CountDownLatch finished = new CountDownLatch(requestCount);   // 전부 끝났는지 세는 카운터

        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                try {
                    startSignal.await();
                    transferService.transferWithPessimisticLock(
                            "pessimistic-" + UUID.randomUUID(),
                            "100-0001", "100-0002", amount);
                    success.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    finished.countDown();
                }
            });
        }

        long startTime = System.nanoTime();   // 처리 시간 측정 시작
        startSignal.countDown();              // 100건 동시에 출발
        finished.await();                     // 100건 모두 끝날 때까지 대기
        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        executor.shutdown();

        long fromAfter = balanceOf("100-0001");
        long toAfter = balanceOf("100-0002");
        long expectedFrom = fromBefore - success.get() * amount;
        long expectedTo = toBefore + success.get() * amount;

        System.out.println("========== 동시성 실험 결과 (비관적 락) ==========");
        System.out.println("요청 수          : " + requestCount);
        System.out.println("성공 / 실패      : " + success.get() + " / " + fail.get());
        System.out.println("출금 계좌 기대 잔액 : " + expectedFrom + "  →  실제: " + fromAfter);
        System.out.println("입금 계좌 기대 잔액 : " + expectedTo + "  →  실제: " + toAfter);
        System.out.println("처리 시간        : " + elapsedMs + " ms");
        System.out.println("=================================================");

        // 이번에는 "정확해야" 통과
        assertThat(success.get()).isEqualTo(requestCount);   // 100건 모두 성공
        assertThat(fromAfter).isEqualTo(expectedFrom);       // 출금 계좌 정확
        assertThat(toAfter).isEqualTo(expectedTo);           // 입금 계좌 정확
    }

    private long balanceOf(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).orElseThrow().getBalance();
    }
}