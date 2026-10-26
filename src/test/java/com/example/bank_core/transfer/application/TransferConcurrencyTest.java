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

// @SpringBootTest: 진짜 서버처럼 Spring을 통째로 켜서 테스트
// @Import(...): 테스트용 연습 창고(MySQL 컨테이너)를 띄워서 연결
// 주의: 이 클래스에 @Transactional을 붙이면 안 됨! 100개 요청이 한 묶음이 되어버려서 동시 실행이 안 됨
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransferConcurrencyTest {

    @Autowired
    TransferService transferService;       // 업무 담당 직원 (실제 코드 그대로 사용)

    @Autowired
    AccountRepository accountRepository;   // 결과 확인용 창고 관리인

    @Test
    void 락_없이_동시에_100번_이체하면_잔액이_틀어진다() throws InterruptedException {
        // ── 준비: 김철수 통장에 10만 원 넣기 ──
        transferService.deposit("setup-" + UUID.randomUUID(), "100-0001", 100_000);

        long fromBefore = balanceOf("100-0001");   // 실험 전 김철수 잔액
        long toBefore = balanceOf("100-0002");     // 실험 전 이영희 잔액

        int requestCount = 100;     // 동시에 보낼 이체 횟수
        long amount = 1_000;        // 한 번에 보낼 금액

        // 직원 32명을 대기시킴 (스레드 풀: 일을 나눠서 동시에 처리하는 일꾼 모임)
        ExecutorService workers = Executors.newFixedThreadPool(32);

        // 출발 신호용 깃발: 100개가 다 준비될 때까지 기다렸다가 "동시에" 출발시키려고 씀
        CountDownLatch startSignal = new CountDownLatch(1);
        // 도착 확인용 카운터: 100개가 다 끝났는지 세려고 씀
        CountDownLatch finished = new CountDownLatch(requestCount);

        // 성공, 실패 개수 (여러 일꾼이 동시에 더해도 숫자가 안 꼬이는 특수 숫자)
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        // ── 실험: 100개의 이체 요청을 준비 ──
        for (int i = 0; i < requestCount; i++) {
            workers.submit(() -> {
                try {
                    startSignal.await();   // 출발 신호 기다리기
                    transferService.transfer(
                            "race-" + UUID.randomUUID(),   // 요청마다 다른 번호표
                            "100-0001", "100-0002", amount);
                    success.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    finished.countDown();  // "나 끝났어" 표시
                }
            });
        }

        startSignal.countDown();   // 탕! 동시에 출발
        finished.await();          // 100개 모두 끝날 때까지 기다림
        workers.shutdown();

        // ── 결과 확인 ──
        long fromAfter = balanceOf("100-0001");
        long toAfter = balanceOf("100-0002");

        // 성공한 횟수만큼 정확히 움직였어야 하는 금액
        long expectedFrom = fromBefore - success.get() * amount;
        long expectedTo = toBefore + success.get() * amount;

        System.out.println("========== 동시성 실험 결과 (락 없음) ==========");
        System.out.println("요청 수          : " + requestCount);
        System.out.println("성공 / 실패      : " + success.get() + " / " + fail.get());
        System.out.println("김철수 기대 잔액 : " + expectedFrom + "  →  실제: " + fromAfter);
        System.out.println("이영희 기대 잔액 : " + expectedTo + "  →  실제: " + toAfter);
        System.out.println("사라진/생긴 돈   : " + ((fromAfter + toAfter) - (fromBefore + toBefore)));
        System.out.println("===============================================");

        // 문제가 "재현"되면 테스트 성공: 실제 잔액이 기대 잔액과 달라야 함
        assertThat(fromAfter).isNotEqualTo(expectedFrom);
    }

    // 계좌번호로 현재 잔액 조회
    private long balanceOf(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).orElseThrow().getBalance();
    }
}