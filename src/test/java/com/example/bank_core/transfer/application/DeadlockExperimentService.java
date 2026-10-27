package com.example.bank_core.transfer.application;

import com.example.bank_core.account.domain.Account;
import com.example.bank_core.account.domain.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * [2주차] 데드락 실험 전용 (테스트 폴더에만 있음 → 실제 서버 코드에는 포함되지 않음)
 * 두 계좌를 비관적 락으로 잠그고 잔액만 옮김 (신청서, 원장 기록은 실험 목적상 생략)
 * 첫 번째 잠금 후 50ms 대기: 두 트랜잭션이 엇갈려 잠글 틈을 일부러 넓혀서 결과를 분명하게 관찰하기 위함
 */
@Component
@RequiredArgsConstructor
public class DeadlockExperimentService {

    private static final long WAIT_AFTER_FIRST_LOCK_MS = 50;

    private final AccountRepository accountRepository;

    /**
     * ① 순서를 맞추지 않음: 항상 "보내는 계좌 → 받는 계좌" 순서로 잠금
     * → 철수→영희, 영희→철수가 동시에 오면 서로 반대 순서로 잠가서 데드락 발생 가능
     */
    @Transactional
    public void transferLockingFromFirst(String fromNumber, String toNumber, long amount) {
        Account from = lock(fromNumber);
        pause();
        Account to = lock(toNumber);

        from.debit(amount);
        to.credit(amount);
    }

    /**
     * ② 순서를 맞춤: 항상 "계좌번호가 작은 쪽 → 큰 쪽" 순서로 잠금 (실제 API와 같은 규칙)
     * → 방향과 상관없이 모두 같은 계좌부터 잠그려고 하므로 엇갈리지 않음
     */
    @Transactional
    public void transferLockingInOrder(String fromNumber, String toNumber, long amount) {
        boolean fromFirst = fromNumber.compareTo(toNumber) < 0;
        Account first = lock(fromFirst ? fromNumber : toNumber);
        pause();
        Account second = lock(fromFirst ? toNumber : fromNumber);

        Account from = fromFirst ? first : second;
        Account to = fromFirst ? second : first;
        from.debit(amount);
        to.credit(amount);
    }

    // SELECT ... FOR UPDATE 로 계좌 행을 잠금
    private Account lock(String accountNumber) {
        return accountRepository.findByAccountNumberForUpdate(accountNumber).orElseThrow();
    }

    private void pause() {
        try {
            Thread.sleep(WAIT_AFTER_FIRST_LOCK_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}