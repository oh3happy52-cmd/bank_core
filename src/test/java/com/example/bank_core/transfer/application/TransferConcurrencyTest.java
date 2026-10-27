package com.example.bank_core.transfer.application;

import com.example.bank_core.TestcontainersConfiguration;
import com.example.bank_core.account.domain.AccountRepository;
import org.junit.jupiter.api.Disabled;
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

// [1주차] 락 없는 이체의 갱신 손실 재현 테스트
// 2주차에 Account의 @Version을 다시 켜서, 이제는 저장 시 버전 검사가 일어나 갱신 손실이 재현되지 않음
// → 실행하지 않도록 @Disabled 처리. 1주차 결과는 docs/week1-lost-update.md 참고
@Disabled("@Version 활성화 이후 락 없는 상태 재현 불가. 결과는 docs/week1-lost-update.md 참고")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransferConcurrencyTest {

    @Autowired
    TransferService transferService;

    @Autowired
    AccountRepository accountRepository;

    @Test
    void 락_없이_동시에_100번_이체하면_잔액이_틀어진다() throws InterruptedException {
        transferService.deposit("setup-" + UUID.randomUUID(), "100-0001", 100_000);

        long fromBefore = balanceOf("100-0001");
        long toBefore = balanceOf("100-0002");

        int requestCount = 100;
        long amount = 1_000;

        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(requestCount);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                try {
                    startSignal.await();
                    transferService.transfer(
                            "race-" + UUID.randomUUID(),
                            "100-0001", "100-0002", amount);
                    success.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    finished.countDown();
                }
            });
        }

        startSignal.countDown();
        finished.await();
        executor.shutdown();

        long fromAfter = balanceOf("100-0001");
        long toAfter = balanceOf("100-0002");
        long expectedFrom = fromBefore - success.get() * amount;
        long expectedTo = toBefore + success.get() * amount;

        System.out.println("========== 동시성 실험 결과 (락 없음) ==========");
        System.out.println("요청 수          : " + requestCount);
        System.out.println("성공 / 실패      : " + success.get() + " / " + fail.get());
        System.out.println("출금 계좌 기대 잔액 : " + expectedFrom + "  →  실제: " + fromAfter);
        System.out.println("입금 계좌 기대 잔액 : " + expectedTo + "  →  실제: " + toAfter);
        System.out.println("===============================================");

        assertThat(fromAfter).isNotEqualTo(expectedFrom);
    }

    private long balanceOf(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).orElseThrow().getBalance();
    }
}