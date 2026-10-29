package com.example.bank_core.settlement.application;

// [무엇] 일일 정산 테스트
// [역할] ① 어제 거래만 종류별로 정확히 합계 내는지 ② 같은 날짜를 두 번 정산해도 한 줄만 남는지
//        ③ 오늘 날짜는 거절하는지 확인
// [누가 쓰나] ./gradlew test 실행 시 JUnit이 실행

import com.example.bank_core.TestcontainersConfiguration;
import com.example.bank_core.common.exception.BusinessException;
import com.example.bank_core.settlement.domain.DailySettlement;
import com.example.bank_core.settlement.domain.DailySettlement.LedgerCheck;
import com.example.bank_core.transfer.application.TransferService;
import com.example.bank_core.transfer.domain.Transfer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SettlementServiceTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

    @Autowired
    SettlementService settlementService;

    @Autowired
    TransferService transferService;

    @Autowired
    JdbcTemplate jdbcTemplate;   // SQL을 직접 실행하는 도구 (테스트 준비용)

    // 테스트마다 깨끗한 상태에서 시작 (다른 테스트가 남긴 거래·정산 결과가 섞이지 않게)
    @BeforeEach
    @AfterEach
    void resetDatabase() {
        jdbcTemplate.update("DELETE FROM daily_settlement");
        jdbcTemplate.update("DELETE FROM ledger_entry");   // 원장이 transfer를 참조하므로 먼저 삭제
        jdbcTemplate.update("DELETE FROM transfer");
        jdbcTemplate.update("UPDATE account SET balance = 0");
    }

    @Test
    void 어제_완료된_거래만_종류별로_합계낸다() {
        // 준비: 입금 2건(5만, 3만) + 이체 1건(2만) → 완료 시각을 어제로 옮김
        Transfer deposit1 = transferService.deposit(newKey(), "100-0001", 50_000);
        Transfer deposit2 = transferService.deposit(newKey(), "100-0001", 30_000);
        Transfer transfer = transferService.transferWithPessimisticLock(newKey(), "100-0001", "100-0002", 20_000);
        moveToYesterday(deposit1.getId());
        moveToYesterday(deposit2.getId());
        moveToYesterday(transfer.getId());

        // 오늘 거래 1건 (정산 대상이 아니므로 합계에 들어가면 안 됨)
        transferService.deposit(newKey(), "100-0002", 7_000);

        DailySettlement result = settlementService.settle(YESTERDAY);
        System.out.println("[어제 정산] 입금 " + result.getDepositCount() + "건 " + result.getDepositAmount()
                + "원, 이체 " + result.getTransferCount() + "건 " + result.getTransferAmount()
                + "원, 원장 검사=" + result.getLedgerCheck());

        assertThat(result.getDepositCount()).isEqualTo(2);
        assertThat(result.getDepositAmount()).isEqualTo(80_000);
        assertThat(result.getTransferCount()).isEqualTo(1);
        assertThat(result.getTransferAmount()).isEqualTo(20_000);
        assertThat(result.getLedgerCheck()).isEqualTo(LedgerCheck.PASSED);
    }

    @Test
    void 같은_날짜를_두_번_정산해도_한_줄만_남는다() {
        DailySettlement first = settlementService.settle(YESTERDAY);
        DailySettlement second = settlementService.settle(YESTERDAY);

        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM daily_settlement WHERE settlement_date = ?", Integer.class, YESTERDAY);
        System.out.println("[중복 정산] 첫 번째 id=" + first.getId() + ", 두 번째 id=" + second.getId()
                + ", 저장된 줄 수=" + rowCount);

        assertThat(second.getId()).isEqualTo(first.getId());   // 두 번째는 기존 결과를 돌려줌
        assertThat(rowCount).isEqualTo(1);                     // DB에도 한 줄만 있음
    }

    @Test
    void 오늘_날짜는_정산할_수_없다() {
        assertThatThrownBy(() -> settlementService.settle(TODAY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("아직 끝나지 않은 날짜");
    }

    // 거래의 완료 시각을 하루 전으로 옮김 (서비스는 항상 "지금" 시각으로 저장하므로, 어제 거래를 흉내 내기 위함)
    private void moveToYesterday(Long transferId) {
        jdbcTemplate.update(
                "UPDATE transfer SET completed_at = completed_at - INTERVAL 1 DAY WHERE id = ?", transferId);
    }

    private String newKey() {
        return "settle-" + UUID.randomUUID();
    }
}