package com.example.bank_core.transfer.application;

import com.example.bank_core.account.domain.Account;
import com.example.bank_core.account.domain.AccountRepository;
import com.example.bank_core.account.domain.AccountStatus;
import com.example.bank_core.common.exception.BusinessException;
import com.example.bank_core.common.exception.ErrorCode;
import com.example.bank_core.ledger.domain.EntryType;
import com.example.bank_core.ledger.domain.LedgerEntry;
import com.example.bank_core.ledger.domain.LedgerEntryRepository;
import com.example.bank_core.transfer.domain.Transfer;
import com.example.bank_core.transfer.domain.TransferRepository;
import com.example.bank_core.transfer.domain.TransferType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * → 비관적 락으로 순서대로 처리
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
     * [1주차] 락 없는 이체 (API에서는 사용하지 않음, 1주차 테스트 기록용으로 남김)
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
     * 계좌를 읽을 때부터 SELECT ... FOR UPDATE로 잠금
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
     * 재시도는 OptimisticLockTransferFacade가 담당
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
     * DB에게 "지금 잔액에서 빼라/더해라"라고 시킴 → 읽기와 쓰기 사이의 틈이 없음
     */
    @Transactional
    public Transfer transferWithAtomicUpdate(String idempotencyKey, String fromAccountNumber,
                                             String toAccountNumber, long amount) {
        validateDifferentAccounts(fromAccountNumber, toAccountNumber);
        validateAmount(amount);

        Account from = findAccount(fromAccountNumber);
        Account to = findAccount(toAccountNumber);
        validateActive(from);
        validateActive(to);

        Long fromId = from.getId();
        Long toId = to.getId();

        // 데드락 방지: id가 작은 계좌부터 UPDATE
        if (fromId < toId) {
            decreaseOrThrow(fromId, amount);
            accountRepository.increaseBalance(toId, amount);
        } else {
            accountRepository.increaseBalance(toId, amount);
            decreaseOrThrow(fromId, amount);
        }

        long fromBalanceAfter = currentBalance(fromId);
        long toBalanceAfter = currentBalance(toId);

        String requestHash = hash("TRANSFER|" + fromAccountNumber + "|" + toAccountNumber + "|" + amount);
        Transfer transfer = transferRepository.save(Transfer.request(
                idempotencyKey, requestHash, TransferType.TRANSFER, fromId, toId, amount));

        ledgerEntryRepository.save(LedgerEntry.record(
                transfer.getId(), fromId, EntryType.DEBIT, amount, fromBalanceAfter));
        ledgerEntryRepository.save(LedgerEntry.record(
                transfer.getId(), toId, EntryType.CREDIT, amount, toBalanceAfter));

        transfer.complete();
        return transfer;
    }

    /**
     * 두 계좌를 비관적 락으로 잠그되, 항상 "계좌번호가 작은 쪽부터" 같은 순서로 잠금
     * → 데드락 방지. 돌려줄 때는 원래 의미(보내는 쪽, 받는 쪽)대로 맞춤
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
        // 1. 출금 (잔액 부족이면 Account 규칙이 INSUFFICIENT_BALANCE 예외 → 전부 롤백)
        from.debit(amount);

        // 2. 입금
        to.credit(amount);

        // 3. 바뀐 잔액을 지금 바로 DB에 반영 (flush)
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

    // 원자적 출금. 수정된 행이 0이면 잔액 부족
    private void decreaseOrThrow(Long accountId, long amount) {
        int updated = accountRepository.decreaseBalance(accountId, amount);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }
    }

    // UPDATE 후 최신 잔액 조회
    private long currentBalance(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow().getBalance();
    }

    // 금액은 0보다 커야 함
    private void validateAmount(long amount) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT);
        }
    }

    // 사용 중인 계좌만 거래 가능
    private void validateActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE, account.getAccountNumber());
        }
    }

    // 같은 계좌끼리 이체 금지
    private void validateDifferentAccounts(String fromAccountNumber, String toAccountNumber) {
        if (fromAccountNumber.equals(toAccountNumber)) {
            throw new BusinessException(ErrorCode.SAME_ACCOUNT);
        }
    }

    // 계좌번호로 계좌 조회 (락 없음). 없으면 ACCOUNT_NOT_FOUND(404)
    private Account findAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, accountNumber));
    }

    // 계좌번호로 계좌 조회 + 비관적 락. 없으면 ACCOUNT_NOT_FOUND(404)
    private Account findAccountForUpdate(String accountNumber) {
        return accountRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, accountNumber));
    }

    // 요청 내용을 64글자 SHA-256 해시로 변환 (멱등성 검사에서 사용)
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