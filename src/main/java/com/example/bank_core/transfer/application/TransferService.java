package com.example.bank_core.transfer.application;

import com.example.bank_core.account.domain.Account;
import com.example.bank_core.account.domain.AccountRepository;
import com.example.bank_core.account.domain.AccountStatus;
import com.example.bank_core.ledger.domain.EntryType;
import com.example.bank_core.ledger.domain.LedgerEntry;
import com.example.bank_core.ledger.domain.LedgerEntryRepository;
import com.example.bank_core.transfer.domain.Transfer;
import com.example.bank_core.transfer.domain.TransferRepository;
import com.example.bank_core.transfer.domain.TransferType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

// @Service: 이 클래스가 업무 로직(서비스)이라고 Spring에 알려 줌
// @RequiredArgsConstructor: final 필드(리포지토리들)를 Spring이 자동으로 연결해 줌
@Service
@RequiredArgsConstructor
public class TransferService {

    // 시스템 계좌(은행 금고)의 계좌번호. 입금할 때 돈이 나오는 쪽으로 사용
    private static final String SYSTEM_ACCOUNT_NUMBER = "SYS-CASH";

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    // 순서대로 잠근 두 계좌를 원래 의미(보내는 쪽, 받는 쪽)대로 담는 상자
    private record LockedAccounts(Account from, Account to) {
    }

    /**
     * 입금: 시스템 계좌 → 고객 계좌 (비관적 락)
     * 모든 입금이 같은 시스템 계좌를 사용하므로, 동시에 입금이 몰리면 충돌이 생김
     * → 비관적 락으로 순서대로 처리 (낙관적 락이면 충돌한 입금이 실패함)
     * 알려진 한계: 입금이 매우 많아지면 시스템 계좌 한 행에 대기가 몰리는 핫스팟이 됨
     */
    @Transactional   // 아래 작업을 하나의 트랜잭션으로 처리. 중간에 실패하면 전부 롤백
    public Transfer deposit(String idempotencyKey, String accountNumber, long amount) {
        LockedAccounts accounts = lockInOrder(SYSTEM_ACCOUNT_NUMBER, accountNumber);

        String requestHash = hash("DEPOSIT|" + accountNumber + "|" + amount);
        return move(idempotencyKey, requestHash, TransferType.DEPOSIT,
                accounts.from(), accounts.to(), amount);
    }

    /**
     * [1주차] 락 없는 이체 (API에서는 더 이상 사용하지 않음, 1주차 테스트 기록용으로 남김)
     * 주의: 2주차에 Account의 @Version을 다시 켠 뒤로는, 저장 시 버전 검사가 자동으로 일어나서
     *       실제로는 낙관적 락과 같은 방식으로 동작함
     */
    @Transactional
    public Transfer transfer(String idempotencyKey, String fromAccountNumber,
                             String toAccountNumber, long amount) {
        validateDifferentAccounts(fromAccountNumber, toAccountNumber);

        Account from = findAccount(fromAccountNumber);
        Account to = findAccount(toAccountNumber);

        String requestHash = hash("TRANSFER|" + fromAccountNumber + "|" + toAccountNumber + "|" + amount);
        return move(idempotencyKey, requestHash, TransferType.TRANSFER, from, to, amount);
    }

    /**
     * [2주차] 비관적 락 이체 (API가 사용하는 최종 방식)
     * 계좌를 읽을 때부터 SELECT ... FOR UPDATE로 잠금.
     * 다른 트랜잭션은 이 트랜잭션이 끝날 때까지 같은 계좌를 잠그지 못하고 기다림
     * 선택 이유: 세 방식 모두 정합성은 같았고, 원자적 UPDATE와 처리 시간 차이가 없었으며,
     *           규칙(debit/credit)을 Account 엔티티에 모아 둘 수 있음 (docs/week2-summary.md 참고)
     */
    @Transactional
    public Transfer transferWithPessimisticLock(String idempotencyKey, String fromAccountNumber,
                                                String toAccountNumber, long amount) {
        validateDifferentAccounts(fromAccountNumber, toAccountNumber);

        LockedAccounts accounts = lockInOrder(fromAccountNumber, toAccountNumber);

        String requestHash = hash("TRANSFER|" + fromAccountNumber + "|" + toAccountNumber + "|" + amount);
        return move(idempotencyKey, requestHash, TransferType.TRANSFER,
                accounts.from(), accounts.to(), amount);
    }

    /**
     * [2주차] 낙관적 락 이체 (재시도 없음, 비교 실험용)
     * 잠그지 않고 읽은 뒤, 저장(UPDATE)할 때 버전 번호를 비교.
     * 그사이 다른 트랜잭션이 먼저 수정했으면 OptimisticLockingFailureException 발생 → 전부 롤백
     * 재시도는 이 메서드를 호출하는 OptimisticLockTransferFacade가 담당
     */
    @Transactional
    public Transfer transferWithOptimisticLock(String idempotencyKey, String fromAccountNumber,
                                               String toAccountNumber, long amount) {
        validateDifferentAccounts(fromAccountNumber, toAccountNumber);

        Account from = findAccount(fromAccountNumber);
        Account to = findAccount(toAccountNumber);

        String requestHash = hash("TRANSFER|" + fromAccountNumber + "|" + toAccountNumber + "|" + amount);
        return move(idempotencyKey, requestHash, TransferType.TRANSFER, from, to, amount);
    }

    /**
     * [2주차] 원자적 UPDATE 이체 (비교 실험용)
     * Java에서 잔액을 계산하지 않고, DB에게 "지금 잔액에서 빼라/더해라"라고 시킴
     * → 읽기와 쓰기 사이의 틈이 없어서 갱신 손실이 생기지 않음
     * → move()는 Java에서 계산(debit/credit)하는 방식이라 섞어 쓰지 않고, 흐름을 따로 만듦
     * → 고객 계좌 간 이체 전용 (잔액이 마이너스가 될 수 있는 시스템 계좌에는 사용하지 않음)
     */
    @Transactional
    public Transfer transferWithAtomicUpdate(String idempotencyKey, String fromAccountNumber,
                                             String toAccountNumber, long amount) {
        validateDifferentAccounts(fromAccountNumber, toAccountNumber);
        validateAmount(amount);   // Account.debit()이 하던 "금액 > 0" 검사를 여기서 직접

        // 계좌 id와 상태를 알기 위해 조회 (잔액은 여기서 읽은 값을 쓰지 않음)
        Account from = findAccount(fromAccountNumber);
        Account to = findAccount(toAccountNumber);
        validateActive(from);     // Account.debit()이 하던 "사용 중인 계좌" 검사를 여기서 직접
        validateActive(to);

        Long fromId = from.getId();
        Long toId = to.getId();

        // 데드락 방지: id가 작은 계좌부터 UPDATE (UPDATE 순간 그 행에 배타 락이 걸리므로 순서를 맞춤)
        if (fromId < toId) {
            decreaseOrThrow(fromId, amount);
            accountRepository.increaseBalance(toId, amount);
        } else {
            accountRepository.increaseBalance(toId, amount);
            decreaseOrThrow(fromId, amount);   // 여기서 잔액 부족이면 위의 입금까지 전부 롤백
        }

        // 거래 후 잔액: UPDATE 직후 같은 트랜잭션에서 다시 조회
        // (이 행들은 지금 내가 잠그고 있으므로, 읽은 값은 방금 내가 바꾼 값)
        long fromBalanceAfter = currentBalance(fromId);
        long toBalanceAfter = currentBalance(toId);

        // 송금 신청서 작성
        String requestHash = hash("TRANSFER|" + fromAccountNumber + "|" + toAccountNumber + "|" + amount);
        Transfer transfer = transferRepository.save(Transfer.request(
                idempotencyKey, requestHash, TransferType.TRANSFER, fromId, toId, amount));

        // 원장 두 줄 기록
        ledgerEntryRepository.save(LedgerEntry.record(
                transfer.getId(), fromId, EntryType.DEBIT, amount, fromBalanceAfter));
        ledgerEntryRepository.save(LedgerEntry.record(
                transfer.getId(), toId, EntryType.CREDIT, amount, toBalanceAfter));

        // 신청서 완료 처리
        transfer.complete();
        return transfer;
    }

    /**
     * 두 계좌를 비관적 락으로 잠그되, 항상 "계좌번호가 작은 쪽부터" 같은 순서로 잠금
     * → A→B, B→A 요청이 동시에 와도 둘 다 같은 계좌부터 잠그려고 하므로 데드락이 생기지 않음
     * → 잠근 순서와 상관없이, 돌려줄 때는 원래 의미(보내는 쪽, 받는 쪽)대로 맞춰서 돌려줌
     */
    private LockedAccounts lockInOrder(String fromAccountNumber, String toAccountNumber) {
        boolean fromFirst = fromAccountNumber.compareTo(toAccountNumber) < 0;
        String firstNumber = fromFirst ? fromAccountNumber : toAccountNumber;
        String secondNumber = fromFirst ? toAccountNumber : fromAccountNumber;

        Account first = findAccountForUpdate(firstNumber);
        Account second = findAccountForUpdate(secondNumber);

        return fromFirst
                ? new LockedAccounts(first, second)
                : new LockedAccounts(second, first);
    }

    /**
     * 입금, 이체가 같이 쓰는 "돈 옮기기" 6단계 (Java에서 잔액을 계산하는 방식)
     */
    private Transfer move(String idempotencyKey, String requestHash, TransferType type,
                          Account from, Account to, long amount) {
        // 1. 보내는 계좌에서 출금 (잔액 부족이면 Account 규칙이 예외 → 전부 롤백)
        from.debit(amount);

        // 2. 받는 계좌에 입금
        to.credit(amount);

        // 3. 바뀐 잔액을 지금 바로 DB에 반영 (flush: 트랜잭션 끝까지 미뤄 둔 UPDATE를 즉시 실행)
        //    - 잔액 UPDATE를 신청서 INSERT보다 먼저 하는 이유:
        //      transfer 테이블에 INSERT하면 MySQL이 외래 키 검사를 위해 account 행에 공유 락(S lock)을 검.
        //      여러 트랜잭션이 S lock을 잡은 뒤 UPDATE(X lock)를 시도하면 데드락이 생길 수 있음
        //    - 낙관적 락에서는 이 시점에 버전 검사가 일어나서, 충돌하면 여기서 예외가 발생함
        accountRepository.flush();

        // 4. 송금 신청서 작성
        Transfer transfer = transferRepository.save(Transfer.request(
                idempotencyKey, requestHash, type, from.getId(), to.getId(), amount));

        // 5. 원장 두 줄 기록 (나간 쪽 DEBIT, 들어온 쪽 CREDIT)
        ledgerEntryRepository.save(LedgerEntry.record(
                transfer.getId(), from.getId(), EntryType.DEBIT, amount, from.getBalance()));
        ledgerEntryRepository.save(LedgerEntry.record(
                transfer.getId(), to.getId(), EntryType.CREDIT, amount, to.getBalance()));

        // 6. 신청서 완료 처리
        transfer.complete();
        return transfer;
    }

    // 원자적 출금 실행. 수정된 행이 0이면 잔액 부족으로 판단해 예외 → 트랜잭션 전체 롤백
    private void decreaseOrThrow(Long accountId, long amount) {
        int updated = accountRepository.decreaseBalance(accountId, amount);
        if (updated == 0) {
            throw new IllegalStateException("잔액이 부족합니다");
        }
    }

    // UPDATE 후 최신 잔액 조회
    private long currentBalance(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow().getBalance();
    }

    // 금액은 0보다 커야 함
    private void validateAmount(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("금액은 0보다 커야 합니다");
        }
    }

    // 사용 중인 계좌만 거래 가능
    private void validateActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("사용할 수 없는 계좌입니다: " + account.getAccountNumber());
        }
    }

    // 같은 계좌끼리 이체 금지
    private void validateDifferentAccounts(String fromAccountNumber, String toAccountNumber) {
        if (fromAccountNumber.equals(toAccountNumber)) {
            throw new IllegalArgumentException("같은 계좌로는 이체할 수 없습니다");
        }
    }

    // 계좌번호로 계좌 조회 (락 없음). 없으면 404
    private Account findAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "계좌를 찾을 수 없습니다: " + accountNumber));
    }

    // 계좌번호로 계좌 조회 + 비관적 락. 없으면 404
    private Account findAccountForUpdate(String accountNumber) {
        return accountRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "계좌를 찾을 수 없습니다: " + accountNumber));
    }

    // 요청 내용을 64글자 SHA-256 해시로 변환 (3주차 멱등성 검사에서 사용)
    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}