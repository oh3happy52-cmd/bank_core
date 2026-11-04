package com.example.bank_core.transfer.application;

// [무엇] 입금·이체 업무 로직
// [역할] 주인 확인 → 멱등성 검사 → 계좌 잠금 → 돈 옮기기 → 이체 기록·원장 저장을 한 트랜잭션으로 처리
//        타행 이체용: 고객 → 보관 계좌(SYS-INTERBANK) 옮기기, 보관 계좌 → 고객 환불
// [누가 쓰나] TransferController(API), InterbankTransferService(타행), 동시성 테스트들

import com.example.bank_core.account.domain.Account;
import com.example.bank_core.account.domain.AccountRepository;
import com.example.bank_core.account.domain.AccountStatus;
import com.example.bank_core.common.exception.BusinessException;
import com.example.bank_core.common.exception.ErrorCode;
import com.example.bank_core.common.security.LoginMember;
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
import java.util.Optional;

// @Service: 이 클래스가 업무 로직(서비스)이라고 Spring에 알려 줌
// @RequiredArgsConstructor: final 필드(리포지토리들)를 Spring이 자동으로 연결해 줌
@Service
@RequiredArgsConstructor
public class TransferService {

    // 시스템 계좌(은행 금고)의 계좌번호. 입금할 때 돈이 나오는 쪽으로 사용
    private static final String SYSTEM_ACCOUNT_NUMBER = "SYS-CASH";

    // 타행 이체 보관 계좌. 다른 은행에 보낼 돈을 잠시 맡아 두는 시스템 계좌
    private static final String INTERBANK_ACCOUNT_NUMBER = "SYS-INTERBANK";

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    // 순서대로 잠근 두 계좌를 원래 의미(보내는 쪽, 받는 쪽)대로 담는 상자
    private record LockedAccounts(Account from, Account to) {
    }

    /**
     * [인증] API용 입금: 계좌 주인 또는 관리자만 가능 → 확인 후 deposit 실행
     * 같은 클래스 안의 메서드를 부르면 안쪽 @Transactional은 따로 적용되지 않지만,
     * 이 메서드의 @Transactional 하나로 전체가 한 트랜잭션으로 묶임
     */
    @Transactional
    public Transfer depositByMember(LoginMember loginMember, String idempotencyKey,
                                    String accountNumber, long amount) {
        if (!loginMember.isAdmin()) {
            validateOwner(accountNumber, loginMember.id());
        }
        return deposit(idempotencyKey, accountNumber, amount);
    }

    /**
     * [인증] API용 이체: 보내는 계좌의 주인만 가능 (관리자도 남의 돈을 옮길 수 없음) → 확인 후 비관적 락 이체 실행
     */
    @Transactional
    public Transfer transferByMember(LoginMember loginMember, String idempotencyKey,
                                     String fromAccountNumber, String toAccountNumber, long amount) {
        validateOwner(fromAccountNumber, loginMember.id());
        return transferWithPessimisticLock(idempotencyKey, fromAccountNumber, toAccountNumber, amount);
    }

    /**
     * [타행] 고객 계좌 → 보관 계좌(SYS-INTERBANK)로 돈 옮기기
     * - 다른 은행에 요청하기 "전에" 우리 장부에서 먼저 빼 둠 (돈이 보관 계좌에 기록되므로 원장 합계는 그대로 0)
     * - receiverSummary: 받는 은행·계좌 (같은 키에 다른 받는 사람이면 IDEMPOTENCY_KEY_REUSED가 나도록 해시에 포함)
     * - 주인 확인은 부르는 쪽(InterbankTransferService)이 먼저 함
     */
    @Transactional
    public Transfer sendToInterbank(String idempotencyKey, String fromAccountNumber,
                                    String receiverSummary, long amount) {
        String requestHash = hash("INTERBANK|" + fromAccountNumber + "|" + receiverSummary + "|" + amount);
        Optional<Transfer> alreadyDone = findAlreadyProcessed(idempotencyKey, requestHash);
        if (alreadyDone.isPresent()) {
            return alreadyDone.get();
        }

        LockedAccounts accounts = lockInOrder(fromAccountNumber, INTERBANK_ACCOUNT_NUMBER);
        return move(idempotencyKey, requestHash, TransferType.INTERBANK,
                accounts.from(), accounts.to(), amount);
    }

    /**
     * [타행] 보관 계좌(SYS-INTERBANK) → 고객 계좌로 환불
     * - 다른 은행이 "확실히 거절"했을 때만 호출됨
     * - idempotencyKey를 "REFUND-번호표"로 고정해서 부름 → 환불 처리가 두 번 실행돼도 돈은 한 번만 돌아감
     */
    @Transactional
    public Transfer refundFromInterbank(String idempotencyKey, Long customerAccountId, long amount) {
        String customerAccountNumber = accountRepository.findAccountNumberById(customerAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, String.valueOf(customerAccountId)));

        String requestHash = hash("REFUND|" + customerAccountNumber + "|" + amount);
        Optional<Transfer> alreadyDone = findAlreadyProcessed(idempotencyKey, requestHash);
        if (alreadyDone.isPresent()) {
            return alreadyDone.get();
        }

        LockedAccounts accounts = lockInOrder(INTERBANK_ACCOUNT_NUMBER, customerAccountNumber);
        return move(idempotencyKey, requestHash, TransferType.REFUND,
                accounts.from(), accounts.to(), amount);
    }

    /**
     * 입금: 시스템 계좌 → 고객 계좌 (비관적 락)
     * 모든 입금이 같은 시스템 계좌를 사용하므로, 동시에 입금이 몰리면 충돌이 생김
     * → 비관적 락으로 순서대로 처리
     * 알려진 한계: 입금이 매우 많아지면 시스템 계좌 한 행에 대기가 몰리는 핫스팟이 됨
     */
    @Transactional   // 아래 작업을 하나의 트랜잭션으로 처리. 중간에 실패하면 전부 롤백
    public Transfer deposit(String idempotencyKey, String accountNumber, long amount) {
        // 0. 멱등성 검사: 같은 키로 이미 처리했으면 돈을 옮기지 않고 그때 결과를 그대로 돌려줌
        String requestHash = hash("DEPOSIT|" + accountNumber + "|" + amount);
        Optional<Transfer> alreadyDone = findAlreadyProcessed(idempotencyKey, requestHash);
        if (alreadyDone.isPresent()) {
            return alreadyDone.get();
        }

        // 1. 처음 보는 키일 때만 계좌를 잠그고 돈을 옮김
        LockedAccounts accounts = lockInOrder(SYSTEM_ACCOUNT_NUMBER, accountNumber);
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

        // 0. 멱등성 검사: 같은 키로 이미 처리했으면 돈을 옮기지 않고 그때 결과를 그대로 돌려줌
        String requestHash = hash("TRANSFER|" + fromAccountNumber + "|" + toAccountNumber + "|" + amount);
        Optional<Transfer> alreadyDone = findAlreadyProcessed(idempotencyKey, requestHash);
        if (alreadyDone.isPresent()) {
            return alreadyDone.get();
        }

        // 1. 처음 보는 키일 때만 계좌를 잠그고 돈을 옮김
        LockedAccounts accounts = lockInOrder(fromAccountNumber, toAccountNumber);
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
     * [인증] 계좌 주인 확인. 주인이 아니면 403
     * - 계좌 객체를 불러오지 않고 "있다/없다"만 확인 (exists 쿼리)
     *   → 뒤에서 비관적 락으로 계좌를 읽을 때, 미리 불러 둔 옛날 잔액이 섞이지 않게 하기 위함
     * - 없는 계좌번호여도 403: 남의 계좌가 존재하는지조차 알려 주지 않음
     */
    private void validateOwner(String accountNumber, Long memberId) {
        if (!accountRepository.existsByAccountNumberAndMemberId(accountNumber, memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    /**
     * 멱등성 검사
     * - 이 키로 처리한 이체가 없음 → 빈 값 (처음 온 요청, 정상 처리하면 됨)
     * - 있음 + 요청 내용(hash)이 같음 → 그 이체를 돌려줌 (재전송이므로 처음 결과를 다시 알려 줌)
     * - 있음 + 요청 내용이 다름 → 422 IDEMPOTENCY_KEY_REUSED (같은 키를 다른 요청에 잘못 사용)
     */
    private Optional<Transfer> findAlreadyProcessed(String idempotencyKey, String requestHash) {
        Optional<Transfer> existing = transferRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent() && !existing.get().getRequestHash().equals(requestHash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        return existing;
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