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

/**
 * [2주차] 양방향 동시 이체에서 잠금 순서에 따른 데드락 발생 비교
 * - 철수→영희 50건 + 영희→철수 50건을 동시에 실행
 * - ① 보내는 계좌부터 잠금 / ② 계좌번호 작은 쪽부터 잠금
 * 주의: 클래스에 @Transactional을 붙이면 안 됨
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, DeadlockExperimentService.class})
class TransferDeadlockTest {

    private static final String A = "100-0001";      // 김철수
    private static final String B = "100-0002";      // 이영희
    private static final int EACH_DIRECTION = 50;    // 방향별 요청 수
    private static final long AMOUNT = 1_000;

    @Autowired
    TransferService transferService;

    @Autowired
    DeadlockExperimentService experiment;

    @Autowired
    AccountRepository accountRepository;

    // 이체 1건을 실행하는 작업 (보내는 계좌, 받는 계좌를 받음)
    interface TransferTask {
        void run(String from, String to);
    }

    // 실행 결과: 방향별 성공 수, 데드락 실패 수, 기타 실패 수
    record Result(int successAtoB, int successBtoA, int deadlock, int otherFail) {
    }

    @Test
    void 잠금_순서를_맞추지_않으면_데드락이_발생한다() throws InterruptedException {
        runAndPrint("① 보내는 계좌부터 잠금 (순서 안 맞춤)",
                (from, to) -> experiment.transferLockingFromFirst(from, to, AMOUNT),
                true);
    }

    @Test
    void 계좌번호_순서로_잠그면_데드락이_발생하지_않는다() throws InterruptedException {
        runAndPrint("② 계좌번호 작은 쪽부터 잠금 (순서 맞춤)",
                (from, to) -> experiment.transferLockingInOrder(from, to, AMOUNT),
                false);
    }

    private void runAndPrint(String title, TransferTask task, boolean expectDeadlock) throws InterruptedException {
        // 준비: 두 계좌 모두 잔액이 넉넉하도록 입금 (잔액 부족 실패가 섞이지 않게)
        transferService.deposit("setup-" + UUID.randomUUID(), A, 100_000);
        transferService.deposit("setup-" + UUID.randomUUID(), B, 100_000);

        long aStart = balanceOf(A);
        long bStart = balanceOf(B);

        Result result = runBothDirections(task);

        long aEnd = balanceOf(A);
        long bEnd = balanceOf(B);
        // 성공한 건만큼만 움직였어야 함: A는 A→B 성공만큼 줄고, B→A 성공만큼 늘어남
        long aExpected = aStart - result.successAtoB() * AMOUNT + result.successBtoA() * AMOUNT;
        long bExpected = bStart + result.successAtoB() * AMOUNT - result.successBtoA() * AMOUNT;

        System.out.println("========== 데드락 실험: " + title + " ==========");
        System.out.println("요청 수            : A→B " + EACH_DIRECTION + "건 + B→A " + EACH_DIRECTION + "건");
        System.out.println("성공               : A→B " + result.successAtoB() + "건 / B→A " + result.successBtoA() + "건");
        System.out.println("데드락으로 실패     : " + result.deadlock() + "건");
        System.out.println("기타 실패          : " + result.otherFail() + "건");
        System.out.println("A 계좌 : 시작 " + aStart + " → 기대 " + aExpected + " / 실제 " + aEnd);
        System.out.println("B 계좌 : 시작 " + bStart + " → 기대 " + bExpected + " / 실제 " + bEnd);
        System.out.println("========================================================");

        // 공통: 실패가 있어도 잔액은 정확해야 함 (실패한 트랜잭션은 통째로 롤백되므로)
        assertThat(aEnd).isEqualTo(aExpected);
        assertThat(bEnd).isEqualTo(bExpected);
        assertThat(result.otherFail()).isZero();

        if (expectDeadlock) {
            assertThat(result.deadlock()).isGreaterThan(0);
        } else {
            assertThat(result.deadlock()).isZero();
            assertThat(result.successAtoB() + result.successBtoA()).isEqualTo(EACH_DIRECTION * 2);
        }
    }

    // A→B 50건, B→A 50건을 섞어서 동시에 실행
    private Result runBothDirections(TransferTask task) throws InterruptedException {
        int total = EACH_DIRECTION * 2;
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(total);

        AtomicInteger successAtoB = new AtomicInteger();
        AtomicInteger successBtoA = new AtomicInteger();
        AtomicInteger deadlock = new AtomicInteger();
        AtomicInteger otherFail = new AtomicInteger();

        for (int i = 0; i < total; i++) {
            boolean aToB = (i % 2 == 0);   // 짝수 번째는 A→B, 홀수 번째는 B→A (번갈아 섞음)
            executor.submit(() -> {
                try {
                    startSignal.await();
                    if (aToB) {
                        task.run(A, B);
                        successAtoB.incrementAndGet();
                    } else {
                        task.run(B, A);
                        successBtoA.incrementAndGet();
                    }
                } catch (Exception e) {
                    if (isDeadlock(e)) {
                        deadlock.incrementAndGet();
                    } else {
                        otherFail.incrementAndGet();
                        System.out.println("기타 실패 원인: " + e);
                    }
                } finally {
                    finished.countDown();
                }
            });
        }

        startSignal.countDown();
        finished.await();
        executor.shutdown();

        return new Result(successAtoB.get(), successBtoA.get(), deadlock.get(), otherFail.get());
    }

    // 예외(와 그 원인들) 중에 MySQL 데드락 메시지가 있는지 확인
    // MySQL은 데드락을 감지하면 한쪽을 롤백시키며 "Deadlock found when trying to get lock" 오류를 냄
    private boolean isDeadlock(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null && message.contains("Deadlock")) {
                return true;
            }
        }
        return false;
    }

    private long balanceOf(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).orElseThrow().getBalance();
    }
}