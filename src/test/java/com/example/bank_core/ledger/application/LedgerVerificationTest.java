package com.example.bank_core.ledger.application;

// [무엇] 원장 정합성 검사 테스트
// [역할] ① 정상 거래만 있으면 검사를 통과하는지 ② 잔액을 장부 없이 직접 바꾸면 불일치를 찾아내는지 확인
// [누가 쓰나] ./gradlew test 실행 시 JUnit이 실행

import com.example.bank_core.TestcontainersConfiguration;
import com.example.bank_core.transfer.application.TransferService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LedgerVerificationTest {

    @Autowired
    TransferService transferService;

    @Autowired
    LedgerVerificationService ledgerVerificationService;

    @Autowired
    JdbcTemplate jdbcTemplate;   // SQL을 직접 실행하는 도구 (테스트 준비, 일부러 데이터를 망가뜨릴 때 사용)

    /**
     * 테스트마다 깨끗한 상태에서 시작
     * 이유: 테스트 클래스들은 같은 테스트 DB를 같이 씀. 그런데 2주차 데드락 실험 코드(DeadlockExperimentService)는
     * 원장 없이 잔액만 바꾸므로, 다른 테스트가 먼저 돌았다면 DB가 이미 어긋나 있을 수 있음
     */
    @BeforeEach
    @AfterEach
    void resetDatabase() {
        jdbcTemplate.update("DELETE FROM ledger_entry");   // 원장이 transfer를 참조하므로 먼저 삭제
        jdbcTemplate.update("DELETE FROM transfer");
        jdbcTemplate.update("UPDATE account SET balance = 0");
    }

    @Test
    void 정상_거래만_있으면_검사를_통과한다() {
        // 입금 5만 원 → 이체 2만 원 (모두 서비스를 거치므로 잔액과 원장이 함께 기록됨)
        transferService.deposit("verify-" + UUID.randomUUID(), "100-0001", 50_000);
        transferService.transferWithPessimisticLock("verify-" + UUID.randomUUID(), "100-0001", "100-0002", 20_000);

        LedgerVerificationResult result = ledgerVerificationService.verify();
        System.out.println("[정상 거래] 검사 결과: " + result);

        assertThat(result.consistent()).isTrue();
        assertThat(result.totalBalance()).isZero();
        assertThat(result.mismatchedAccounts()).isEmpty();
        assertThat(result.unbalancedTransferIds()).isEmpty();
    }

    @Test
    void 잔액을_장부_없이_직접_바꾸면_불일치를_찾아낸다() {
        transferService.deposit("verify-" + UUID.randomUUID(), "100-0001", 10_000);

        // 버그나 잘못된 수작업을 흉내: 장부(원장)에 기록 없이 이영희 잔액만 500원 올림
        jdbcTemplate.update("UPDATE account SET balance = balance + 500 WHERE account_number = '100-0002'");

        LedgerVerificationResult result = ledgerVerificationService.verify();
        System.out.println("[잔액 조작] 검사 결과: " + result);

        assertThat(result.consistent()).isFalse();
        assertThat(result.totalBalance()).isEqualTo(500);   // 없던 돈 500원이 생겨서 합계가 0이 아님
        assertThat(result.mismatchedAccounts()).hasSize(1);
        assertThat(result.mismatchedAccounts().get(0).accountNumber()).isEqualTo("100-0002");
        assertThat(result.mismatchedAccounts().get(0).balance()).isEqualTo(500);
        assertThat(result.mismatchedAccounts().get(0).ledgerBalance()).isZero();
    }
}