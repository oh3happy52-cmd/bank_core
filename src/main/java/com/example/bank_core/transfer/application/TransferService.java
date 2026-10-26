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

// @Service: "이 클래스는 업무 담당 직원이에요"라고 Spring에 알려 줌
// @RequiredArgsConstructor: 아래 final 필드(창고 관리인들)를 Spring이 자동으로 연결해 줌
@Service
@RequiredArgsConstructor
public class TransferService {

    // 은행 금고 통장의 계좌번호. 입금할 때 "돈이 나오는 곳"으로 씀
    private static final String SYSTEM_ACCOUNT_NUMBER = "SYS-CASH";

    // 창고 관리인 3명 (통장, 송금 신청서, 가계부)
    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    /**
     * 입금: 은행 금고 → 고객 통장
     */
    @Transactional   // 아래 일들을 한 묶음으로 처리. 중간에 실패하면 전부 없던 일로 되돌림
    public Transfer deposit(String idempotencyKey, String accountNumber, long amount) {
        // 은행 금고 통장과 고객 통장을 창고에서 꺼내 옴
        Account system = findSystemAccount();
        Account customer = findAccount(accountNumber);

        // 실제 돈 옮기기는 아래 공통 메서드가 처리 (금고 → 고객)
        String requestHash = hash("DEPOSIT|" + accountNumber + "|" + amount);
        return move(idempotencyKey, requestHash, TransferType.DEPOSIT, system, customer, amount);
    }

    /**
     * 이체: 고객 통장 → 다른 고객 통장
     * [1주차] 일부러 락 없이 만듦. 동시에 여러 요청이 오면 잔액이 틀어지는 문제를 재현하기 위함
     */
    @Transactional
    public Transfer transfer(String idempotencyKey, String fromAccountNumber,
                             String toAccountNumber, long amount) {
        // 나한테 나를 보내는 건 의미가 없으니 막음
        if (fromAccountNumber.equals(toAccountNumber)) {
            throw new IllegalArgumentException("같은 계좌로는 이체할 수 없습니다");
        }

        // 보내는 사람, 받는 사람 통장을 창고에서 꺼내 옴 (없으면 404)
        Account from = findAccount(fromAccountNumber);
        Account to = findAccount(toAccountNumber);

        String requestHash = hash("TRANSFER|" + fromAccountNumber + "|" + toAccountNumber + "|" + amount);
        return move(idempotencyKey, requestHash, TransferType.TRANSFER, from, to, amount);
    }

    /**
     * 입금, 이체가 같이 쓰는 "돈 옮기기" 5단계
     * 같은 코드를 두 번 쓰지 않으려고 하나로 모음
     */
    private Transfer move(String idempotencyKey, String requestHash, TransferType type,
                          Account from, Account to, long amount) {
        // 1. 송금 신청서 작성 ("처리 중" 상태로 시작)
        Transfer transfer = transferRepository.save(Transfer.request(
                idempotencyKey, requestHash, type, from.getId(), to.getId(), amount));

        // 2. 보내는 통장에서 돈 빼기
        //    → 잔액이 부족하면 Account 안의 규칙이 오류를 던짐
        //    → 그러면 @Transactional 덕분에 1번 신청서까지 전부 취소됨
        from.debit(amount);

        // 3. 받는 통장에 돈 넣기
        to.credit(amount);

        // 4. 가계부 두 줄 쓰기 (나간 쪽 DEBIT, 들어온 쪽 CREDIT)
        //    balance_after: 이 거래 직후 잔액을 같이 적어 둠 (나중에 추적할 때 편함)
        ledgerEntryRepository.save(LedgerEntry.record(
                transfer.getId(), from.getId(), EntryType.DEBIT, amount, from.getBalance()));
        ledgerEntryRepository.save(LedgerEntry.record(
                transfer.getId(), to.getId(), EntryType.CREDIT, amount, to.getBalance()));

        // 5. 신청서에 "완료" 도장
        transfer.complete();

        // 통장 잔액은 save를 안 해도 저장됨
        // → 트랜잭션 안에서 꺼낸 통장은 Spring이 지켜보다가, 바뀐 내용을 끝날 때 자동 반영 (변경 감지)
        return transfer;
    }

    // 계좌번호로 통장 꺼내기. 없으면 "그런 통장 없어요"(404)
    private Account findAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "계좌를 찾을 수 없습니다: " + accountNumber));
    }

    // 은행 금고 통장 꺼내기. 이게 없으면 시스템 설정이 잘못된 것이라 500 오류
    private Account findSystemAccount() {
        return accountRepository.findByAccountNumber(SYSTEM_ACCOUNT_NUMBER)
                .orElseThrow(() -> new IllegalStateException("은행 금고 계좌가 없습니다"));
    }

    // 요청 내용을 64글자 "지문"(SHA-256)으로 바꿈
    // → 3주차에 "같은 번호표인데 내용이 다른 요청"을 잡아낼 때 사용
    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);   // 바이트를 16진수 글자로 바꿈
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}