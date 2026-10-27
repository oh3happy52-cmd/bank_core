package com.example.bank_core.transfer.application;

import com.example.bank_core.account.domain.Account;
import com.example.bank_core.account.domain.AccountRepository;
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

    /**
     * 입금: 시스템 계좌 → 고객 계좌
     */
    @Transactional   // 아래 작업을 하나의 트랜잭션으로 처리. 중간에 실패하면 전부 롤백
    public Transfer deposit(String idempotencyKey, String accountNumber, long amount) {
        Account system = findSystemAccount();
        Account customer = findAccount(accountNumber);

        String requestHash = hash("DEPOSIT|" + accountNumber + "|" + amount);
        return move(idempotencyKey, requestHash, TransferType.DEPOSIT, system, customer, amount);
    }

    /**
     * [1주차] 락 없는 이체
     * 동시에 요청이 몰리면 갱신 손실(Lost Update)이 발생함. 비교 실험용으로 남겨 둠
     */
    @Transactional
    public Transfer transfer(String idempotencyKey, String fromAccountNumber,
                             String toAccountNumber, long amount) {
        validateDifferentAccounts(fromAccountNumber, toAccountNumber);

        // 일반 조회: 락 없이 읽음 → 여러 트랜잭션이 같은 잔액을 동시에 읽을 수 있음
        Account from = findAccount(fromAccountNumber);
        Account to = findAccount(toAccountNumber);

        String requestHash = hash("TRANSFER|" + fromAccountNumber + "|" + toAccountNumber + "|" + amount);
        return move(idempotencyKey, requestHash, TransferType.TRANSFER, from, to, amount);
    }

    /**
     * [2주차] 비관적 락 이체
     * 계좌를 읽을 때부터 SELECT ... FOR UPDATE로 잠금.
     * 다른 트랜잭션은 이 트랜잭션이 끝날 때까지 같은 계좌를 잠그지 못하고 기다림
     */
    @Transactional
    public Transfer transferWithPessimisticLock(String idempotencyKey, String fromAccountNumber,
                                                String toAccountNumber, long amount) {
        validateDifferentAccounts(fromAccountNumber, toAccountNumber);

        // 데드락 방지: 두 계좌 중 계좌번호가 "작은 쪽부터" 항상 같은 순서로 잠금
        // → A→B, B→A 요청이 동시에 와도 둘 다 같은 계좌부터 잠그려고 하므로 서로 엇갈려 기다리지 않음
        boolean fromFirst = fromAccountNumber.compareTo(toAccountNumber) < 0;
        String firstNumber = fromFirst ? fromAccountNumber : toAccountNumber;
        String secondNumber = fromFirst ? toAccountNumber : fromAccountNumber;

        Account first = findAccountForUpdate(firstNumber);
        Account second = findAccountForUpdate(secondNumber);

        // 잠근 순서와 상관없이, 원래 의미대로 보내는 쪽(from)과 받는 쪽(to)을 다시 맞춤
        Account from = fromFirst ? first : second;
        Account to = fromFirst ? second : first;

        String requestHash = hash("TRANSFER|" + fromAccountNumber + "|" + toAccountNumber + "|" + amount);
        return move(idempotencyKey, requestHash, TransferType.TRANSFER, from, to, amount);
    }

    /**
     * 입금, 이체가 같이 쓰는 "돈 옮기기" 6단계
     */
    private Transfer move(String idempotencyKey, String requestHash, TransferType type,
                          Account from, Account to, long amount) {
        // 1. 보내는 계좌에서 출금 (잔액 부족이면 Account 규칙이 예외 → 전부 롤백)
        from.debit(amount);

        // 2. 받는 계좌에 입금
        to.credit(amount);

        // 3. 바뀐 잔액을 지금 바로 DB에 반영 (flush: 트랜잭션 끝까지 미뤄 둔 UPDATE를 즉시 실행)
        //    [1주차] 잔액 UPDATE를 신청서 INSERT보다 먼저 하는 이유:
        //    transfer 테이블에 INSERT하면 MySQL이 외래 키 검사를 위해
        //    참조하는 account 행에 공유 락(S lock)을 검.
        //    락 없는 버전에서는 여러 트랜잭션이 동시에 S lock을 잡은 뒤 UPDATE(X lock)를 시도하다 데드락 발생.
        //    비관적 락 버전은 처음부터 X lock을 잡고 있어서 이 문제와 무관함
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

    // 시스템 계좌 조회. 없으면 설정 오류이므로 500
    private Account findSystemAccount() {
        return accountRepository.findByAccountNumber(SYSTEM_ACCOUNT_NUMBER)
                .orElseThrow(() -> new IllegalStateException("은행 금고 계좌가 없습니다"));
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