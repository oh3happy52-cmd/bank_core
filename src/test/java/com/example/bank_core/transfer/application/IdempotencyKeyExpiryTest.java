package com.example.bank_core.transfer.application;

// [무엇] 멱등성 키 24시간 만료 테스트
// [역할] ① 24시간 안의 같은 번호표는 처음 영수증을 돌려주고 돈을 다시 옮기지 않는지
//        ② 24시간이 지난 번호표는 새 요청으로 처리되는지 (같은 내용, 다른 내용 모두)
//        ③ 정리 작업이 유효기간 지난 메모만 지우고, 이체 기록(영수증)은 남기는지 확인
//        24시간을 기다릴 수 없으므로, SQL로 메모의 유효기간을 "만든 시각 1초 전"으로 당겨서 만료 상태를 만듦
// [누가 쓰나] ./gradlew test 실행 시 JUnit이 실행 (CI에서도 자동 실행)

import com.example.bank_core.TestcontainersConfiguration;
import com.example.bank_core.account.domain.AccountRepository;
import com.example.bank_core.common.exception.BusinessException;
import com.example.bank_core.common.exception.ErrorCode;
import com.example.bank_core.transfer.domain.IdempotencyKeyRepository;
import com.example.bank_core.transfer.domain.Transfer;
import com.example.bank_core.transfer.domain.TransferRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IdempotencyKeyExpiryTest {

    private static final String ACCOUNT = "100-0001";   // V2에서 넣은 김철수 계좌

    @Autowired TransferService transferService;
    @Autowired IdempotencyKeyCleanupService idempotencyKeyCleanupService;
    @Autowired IdempotencyKeyRepository idempotencyKeyRepository;
    @Autowired TransferRepository transferRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired JdbcTemplate jdbcTemplate;   // SQL을 직접 실행하는 도구 (유효기간을 일부러 지나게 만들 때 사용)

    @Test
    void 유효기간_안에_같은_번호표로_다시_보내면_처음_영수증을_돌려주고_돈은_한_번만_옮긴다() {
        String key = newKey();
        long before = balanceOf(ACCOUNT);

        Transfer first = transferService.deposit(key, ACCOUNT, 1_000);
        Transfer retry = transferService.deposit(key, ACCOUNT, 1_000);   // 응답을 못 받아 다시 보낸 상황

        assertThat(retry.getId()).isEqualTo(first.getId());              // 같은 영수증
        assertThat(balanceOf(ACCOUNT) - before).isEqualTo(1_000);         // 돈은 한 번만
    }

    @Test
    void 유효기간_안에_같은_번호표로_다른_내용을_보내면_422_IDEMPOTENCY_KEY_REUSED() {
        String key = newKey();
        transferService.deposit(key, ACCOUNT, 1_000);

        assertThatThrownBy(() -> transferService.deposit(key, ACCOUNT, 5_000))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void 유효기간이_지난_번호표는_같은_내용이어도_새_요청으로_처리한다() {
        String key = newKey();
        long before = balanceOf(ACCOUNT);

        Transfer november = transferService.deposit(key, ACCOUNT, 1_000);   // 예) 11월 월세
        expire(key);                                                         // 24시간이 지났다고 가정
        Transfer december = transferService.deposit(key, ACCOUNT, 1_000);   // 예) 12월 월세, 같은 번호표

        long sameKeyTransfers = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer WHERE idempotency_key = ?", Long.class, key);

        System.out.println("[만료 후 같은 번호표] 처음 영수증 " + november.getId() + " → 새 영수증 " + december.getId()
                + ", 같은 번호표의 영수증 수 " + sameKeyTransfers);

        assertThat(december.getId()).isNotEqualTo(november.getId());       // 새 영수증
        assertThat(balanceOf(ACCOUNT) - before).isEqualTo(2_000);           // 돈도 새로 옮김 (두 번)
        assertThat(sameKeyTransfers).isEqualTo(2);                          // 영수증은 둘 다 남아 있음
        assertThat(idempotencyKeyRepository.findByIdempotencyKey(key))      // 메모는 새 영수증을 가리킴
                .get().extracting("transferId").isEqualTo(december.getId());
    }

    @Test
    void 유효기간이_지난_번호표는_다른_내용으로도_쓸_수_있다() {
        String key = newKey();
        transferService.deposit(key, ACCOUNT, 1_000);
        expire(key);

        Transfer renewed = transferService.deposit(key, ACCOUNT, 5_000);    // 만료 전이라면 422였을 요청

        assertThat(renewed.getAmount()).isEqualTo(5_000);
    }

    @Test
    void 정리_작업은_유효기간_지난_메모만_지우고_영수증은_남긴다() {
        String expiredKey = newKey();
        String freshKey = newKey();
        Transfer expiredTransfer = transferService.deposit(expiredKey, ACCOUNT, 1_000);
        transferService.deposit(freshKey, ACCOUNT, 1_000);
        expire(expiredKey);

        int deleted = idempotencyKeyCleanupService.deleteExpired();
        System.out.println("[정리 작업] 지운 메모 수: " + deleted);

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(idempotencyKeyRepository.findByIdempotencyKey(expiredKey)).isEmpty();   // 지난 메모는 삭제
        assertThat(idempotencyKeyRepository.findByIdempotencyKey(freshKey)).isPresent();   // 유효한 메모는 남음
        assertThat(transferRepository.findById(expiredTransfer.getId())).isPresent();      // 영수증은 그대로
    }

    // 메모의 유효기간을 "만든 시각 1초 전"으로 당김 → 이미 지난 상태
    // (DB의 현재 시각이 아니라 만든 시각 기준으로 계산해서, DB와 서버의 시간대가 달라도 결과가 같음)
    private void expire(String key) {
        jdbcTemplate.update(
                "UPDATE idempotency_key SET expires_at = created_at - INTERVAL 1 SECOND WHERE idempotency_key = ?", key);
    }

    private long balanceOf(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).orElseThrow().getBalance();
    }

    private String newKey() {
        return "expiry-" + UUID.randomUUID();
    }
}
