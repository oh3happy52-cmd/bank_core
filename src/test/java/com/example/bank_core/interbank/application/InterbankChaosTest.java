package com.example.bank_core.interbank.application;

// [무엇] 타행 이체 장애 실험 테스트 (1,000건)
// [역할] 모의 타행에 실패 30%, 지연 20%를 넣고 타행 이체 1,000건을 보낸 뒤,
//        고장을 끄고 자동 확인 작업으로 정리했을 때
//        ① 끝까지 모름 0건 ② 이중 지급 0건 ③ 돈 사라짐 0건 ④ 원장 검사 통과 를 증명
// [누가 쓰나] ./gradlew test 실행 시 JUnit이 실행 (CI에서도 자동 실행)

import com.example.bank_core.TestcontainersConfiguration;
import com.example.bank_core.account.domain.AccountRepository;
import com.example.bank_core.common.security.LoginMember;
import com.example.bank_core.interbank.application.InterbankTransferService.ReconcileResult;
import com.example.bank_core.interbank.domain.InterbankTransfer;
import com.example.bank_core.interbank.domain.InterbankTransfer.Status;
import com.example.bank_core.interbank.domain.InterbankTransferRepository;
import com.example.bank_core.ledger.application.LedgerVerificationService;
import com.example.bank_core.mockbank.MockBank;
import com.example.bank_core.mockbank.MockBank.Stats;
import com.example.bank_core.transfer.application.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// RANDOM_PORT: 서버를 실제로 임의의 포트에 켬 → 우리 은행이 모의 타행을 진짜 HTTP로 부름 (타임아웃이 실제로 생김)
// properties: 실험용 설정
//   - 타임아웃 300ms, 모의 타행 지연 600ms → 지연이면 반드시 타임아웃
//   - 자동 확인 작업 간격을 아주 길게(10분) → 실험 중 끼어들지 않게 하고, 테스트가 직접 호출
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "interbank.timeout-millis=300",
                "interbank.reconcile-delay-millis=600000"
        })
@Import(TestcontainersConfiguration.class)
class InterbankChaosTest {

    private static final int TOTAL = 1_000;          // 타행 이체 건수
    private static final int REJECT_EVERY = 20;      // 20건 중 1건은 없는 계좌(999) → 총 50건 환불 대상
    private static final long AMOUNT = 1_000;        // 건당 금액
    private static final LoginMember CHULSOO = new LoginMember(1L, "USER");   // V5에서 넣은 김철수(회원 1번)

    @Autowired InterbankTransferService interbankTransferService;
    @Autowired InterbankTransferRepository interbankTransferRepository;
    @Autowired TransferService transferService;
    @Autowired AccountRepository accountRepository;
    @Autowired LedgerVerificationService ledgerVerificationService;
    @Autowired MockBank mockBank;

    @Test
    void 실패30_지연20_상황에서_1000건을_보내도_이중지급과_돈사라짐이_없다() throws InterruptedException {
        // 준비: 김철수 계좌에 충분한 돈, 모의 타행 초기화 후 고장 켜기
        transferService.deposit("chaos-setup-" + UUID.randomUUID(), "100-0001", 100_000_000);
        mockBank.reset();
        mockBank.configure(0.30, 0.20, 600);

        long chulsooBefore = balanceOf("100-0001");
        long interbankBefore = balanceOf("SYS-INTERBANK");

        // 1. 타행 이체 1,000건을 10개씩 동시에
        Map<Status, AtomicInteger> firstResult = new ConcurrentHashMap<>();
        AtomicInteger errors = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch finished = new CountDownLatch(TOTAL);
        String runId = UUID.randomUUID().toString().substring(0, 8);

        for (int i = 0; i < TOTAL; i++) {
            final int n = i;
            executor.submit(() -> {
                try {
                    String toAccount = (n % REJECT_EVERY == 0)
                            ? String.format("999-%04d", n)     // 없는 계좌 → 다른 은행이 거절
                            : String.format("200-%04d", n);    // 정상 계좌
                    InterbankTransfer result = interbankTransferService.send(
                            CHULSOO, "chaos-" + runId + "-" + n, "100-0001", "088", toAccount, AMOUNT);
                    firstResult.computeIfAbsent(result.getStatus(), s -> new AtomicInteger()).incrementAndGet();
                } catch (Exception e) {
                    errors.incrementAndGet();
                    System.out.println("예외 발생: " + e);   // 실패 원인을 보기 위해 출력
                } finally {
                    finished.countDown();
                }
            });
        }
        finished.await();
        executor.shutdown();

        // 2. 고장을 끄고, 자동 확인 작업을 남은 게 없을 때까지 반복 (최대 10번)
        mockBank.configure(0.0, 0.0, 600);
        int rounds = 0;
        ReconcileResult reconcile;
        do {
            reconcile = interbankTransferService.reconcileAll();
            rounds++;
        } while (reconcile.stillUnknown() > 0 && rounds < 10);

        // 3. 결과 집계
        List<InterbankTransfer> all = interbankTransferRepository.findAll();
        long completedCount = all.stream().filter(t -> t.getStatus() == Status.COMPLETED).count();
        long failedCount = all.stream().filter(t -> t.getStatus() == Status.FAILED).count();
        long unresolvedCount = all.stream().filter(t -> !t.isFinished()).count();
        long completedAmount = all.stream().filter(t -> t.getStatus() == Status.COMPLETED).mapToLong(InterbankTransfer::getAmount).sum();

        // 환불했는데 다른 은행에서는 입금 처리된 건 (있으면 이중 지급)
        long refundedButDeposited = all.stream()
                .filter(t -> t.getStatus() == Status.FAILED)
                .filter(t -> mockBank.find(t.getRequestId())
                        .map(d -> d.result() == MockBank.Result.SUCCESS).orElse(false))
                .count();

        Stats bank = mockBank.stats();
        long chulsooLost = chulsooBefore - balanceOf("100-0001");
        long interbankGained = balanceOf("SYS-INTERBANK") - interbankBefore;
        boolean ledgerOk = ledgerVerificationService.verify().consistent();

        System.out.println("================ 타행 이체 장애 실험 (실패 30%, 지연 20%) ================");
        System.out.println("보낸 건수                   : " + TOTAL + " (없는 계좌 " + (TOTAL / REJECT_EVERY) + "건 포함), 예외 " + errors.get());
        System.out.println("첫 응답 직후 상태            : " + firstResult);
        System.out.println("자동 확인 반복 횟수          : " + rounds);
        System.out.println("최종: 완료 / 실패(환불) / 모름 : " + completedCount + " / " + failedCount + " / " + unresolvedCount);
        System.out.println("다른 은행 입금 건수·총액      : " + bank.successCount() + "건, " + bank.successAmount() + "원");
        System.out.println("우리 쪽 완료 건수·총액        : " + completedCount + "건, " + completedAmount + "원");
        System.out.println("같은 번호표 재전송(중복 처리 안 됨): " + bank.duplicateRequestCount() + "번");
        System.out.println("환불했는데 입금된 건(이중 지급) : " + refundedButDeposited);
        System.out.println("김철수가 잃은 돈 / 보관 계좌 증가 : " + chulsooLost + "원 / " + interbankGained + "원");
        System.out.println("원장 검사                    : " + (ledgerOk ? "통과" : "실패"));
        System.out.println("=========================================================================");

        assertThat(errors.get()).isZero();
        assertThat(unresolvedCount).isZero();                                   // 끝까지 모름 0건
        assertThat(completedCount + failedCount).isEqualTo(TOTAL);
        assertThat(failedCount).isEqualTo(TOTAL / REJECT_EVERY);                // 없는 계좌 50건만 환불
        assertThat((long) bank.successCount()).isEqualTo(completedCount);       // 이중 지급 0건 (건수)
        assertThat(bank.successAmount()).isEqualTo(completedAmount);            // 이중 지급 0건 (금액)
        assertThat(refundedButDeposited).isZero();                              // 환불한 건은 입금되지 않음
        assertThat(chulsooLost).isEqualTo(completedAmount);                     // 돈 사라짐 0 (환불분은 돌아옴)
        assertThat(interbankGained).isEqualTo(completedAmount);                 // 보관 계좌 = 다른 은행에 줄 돈
        assertThat(ledgerOk).isTrue();                                          // 원장 검사 통과
    }

    private long balanceOf(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).orElseThrow().getBalance();
    }
}