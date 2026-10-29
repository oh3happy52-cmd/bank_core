package com.example.bank_core.transfer.application;

// [무엇] 멱등성 동시성 테스트
// [역할] 같은 Idempotency-Key로 입금 요청 10건을 동시에 보내도, 실제 입금은 1번만 되는지 확인
// [누가 쓰나] ./gradlew test 실행 시 JUnit이 실행

import com.example.bank_core.TestcontainersConfiguration;
import com.example.bank_core.account.domain.AccountRepository;
import com.example.bank_core.transfer.domain.Transfer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// 주의: 클래스에 @Transactional을 붙이면 안 됨 (10건이 하나의 트랜잭션으로 묶여 동시 실행이 안 됨)
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransferIdempotencyTest {

    @Autowired
    TransferService transferService;

    @Autowired
    AccountRepository accountRepository;

    @Test
    void 같은_키로_동시에_10번_입금해도_한_번만_입금된다() throws InterruptedException {
        String key = "idem-test-" + UUID.randomUUID();   // 10건 모두 같은 키 사용
        long amount = 1_000;
        int requestCount = 10;

        long before = balanceOf("100-0001");

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch startSignal = new CountDownLatch(1);           // 동시에 출발시키기 위한 신호
        CountDownLatch finished = new CountDownLatch(requestCount);   // 전부 끝났는지 세는 카운터

        AtomicInteger success = new AtomicInteger();    // 정상 응답 (처음 처리 + 처음 결과를 다시 받은 것)
        AtomicInteger conflict = new AtomicInteger();   // DB UNIQUE 규칙에 걸린 것 (API에서는 409)
        AtomicInteger other = new AtomicInteger();      // 그 밖의 예외 (0이어야 함)
        Set<Long> transferIds = ConcurrentHashMap.newKeySet();   // 성공 응답에 들어 있던 transferId 모음

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                try {
                    startSignal.await();
                    Transfer result = transferService.deposit(key, "100-0001", amount);
                    transferIds.add(result.getId());
                    success.incrementAndGet();
                } catch (DataIntegrityViolationException e) {
                    conflict.incrementAndGet();
                } catch (Exception e) {
                    other.incrementAndGet();
                } finally {
                    finished.countDown();
                }
            });
        }

        startSignal.countDown();   // 10건 동시에 출발
        finished.await();          // 10건 모두 끝날 때까지 대기
        executor.shutdown();

        long after = balanceOf("100-0001");

        System.out.println("========== 멱등성 동시 요청 결과 ==========");
        System.out.println("요청 수 (모두 같은 키) : " + requestCount);
        System.out.println("성공 / 충돌(409) / 기타 : " + success.get() + " / " + conflict.get() + " / " + other.get());
        System.out.println("성공 응답의 transferId 종류 : " + transferIds);
        System.out.println("잔액 변화 : " + before + " → " + after + " (기대: +" + amount + ")");
        System.out.println("==========================================");

        assertThat(after - before).isEqualTo(amount);                     // 돈은 딱 1번만 들어감
        assertThat(transferIds).hasSize(1);                               // 성공 응답은 모두 같은 이체 1건
        assertThat(success.get() + conflict.get()).isEqualTo(requestCount); // 모든 요청이 성공 또는 409로 끝남
        assertThat(other.get()).isZero();                                  // 예상 못 한 예외 없음
    }

    private long balanceOf(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).orElseThrow().getBalance();
    }
}