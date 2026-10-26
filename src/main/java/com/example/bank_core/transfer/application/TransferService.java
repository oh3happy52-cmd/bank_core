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

@Service
@RequiredArgsConstructor
public class TransferService {

    private static final String SYSTEM_ACCOUNT_NUMBER = "SYS-CASH";   // 은행 금고 통장

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional   // 아래 5단계를 한 묶음으로: 전부 성공 또는 전부 취소
    public Transfer deposit(String idempotencyKey, String accountNumber, long amount) {
        Account system = accountRepository.findByAccountNumber(SYSTEM_ACCOUNT_NUMBER)
                .orElseThrow(() -> new IllegalStateException("은행 금고 계좌가 없습니다"));
        Account customer = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "계좌를 찾을 수 없습니다: " + accountNumber));

        // 1. 송금 신청서 작성 (처리 중)
        String requestHash = hash("DEPOSIT|" + accountNumber + "|" + amount);
        Transfer transfer = transferRepository.save(Transfer.request(
                idempotencyKey, requestHash, TransferType.DEPOSIT,
                system.getId(), customer.getId(), amount));

        // 2. 금고에서 돈 빼기, 3. 고객 통장에 돈 넣기
        system.debit(amount);
        customer.credit(amount);

        // 4. 가계부 두 줄 쓰기
        ledgerEntryRepository.save(LedgerEntry.record(
                transfer.getId(), system.getId(), EntryType.DEBIT, amount, system.getBalance()));
        ledgerEntryRepository.save(LedgerEntry.record(
                transfer.getId(), customer.getId(), EntryType.CREDIT, amount, customer.getBalance()));

        // 5. 완료 도장
        transfer.complete();
        return transfer;
    }

    // 요청 내용을 64글자 지문(SHA-256)으로 바꿈. 3주차 멱등성 검사에서 사용
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