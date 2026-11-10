package com.example.bank_core.account.domain;

// [무엇] 계좌(account 테이블)를 DB에서 읽고 쓰는 창구
// [역할] 계좌 조회, 비관적 락 조회, 원자적 UPDATE, 계좌 주인 확인, 계좌번호만 조회
// [누가 쓰나] AccountService, TransferService, InterbankTransferService

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    // 계좌번호로 조회 (락 없음)
    Optional<Account> findByAccountNumber(String accountNumber);

    // [2주차] 비관적 락: 계좌번호로 조회하면서 행을 잠금
    // → 실제 SQL: SELECT ... FROM account WHERE account_number = ? FOR UPDATE
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);

    // [2주차] 원자적 UPDATE: 출금
    // → DB가 "그 순간의 최신 잔액"으로 직접 계산. 읽기와 쓰기 사이의 틈이 없음
    // → 잔액이 금액보다 적으면 조건에 안 맞아서 아무것도 안 바뀜 (반환값 0 = 잔액 부족)
    // → @Version 자동 검사를 거치지 않으므로, 버전 번호를 직접 1 올려서 낙관적 락과 충돌하지 않게 함
    // → clearAutomatically: 실행 후 JPA가 기억하던 옛날 값을 지워서, 다시 조회하면 최신 값을 읽게 함
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Account a set a.balance = a.balance - :amount, a.version = a.version + 1 " +
           "where a.id = :id and a.balance >= :amount")
    int decreaseBalance(@Param("id") Long id, @Param("amount") long amount);

    // [2주차] 원자적 UPDATE: 입금
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Account a set a.balance = a.balance + :amount, a.version = a.version + 1 " +
           "where a.id = :id")
    int increaseBalance(@Param("id") Long id, @Param("amount") long amount);

    // [인증] 이 계좌번호의 주인이 이 회원인가? (참/거짓만 확인)
    // → 실제 SQL: SELECT ... FROM account WHERE account_number = ? AND member_id = ? (있으면 true)
    // → 계좌 객체를 불러오지 않고 "있다/없다"만 보므로, 뒤에서 비관적 락으로 계좌를 읽을 때 옛날 값이 섞이지 않음
    boolean existsByAccountNumberAndMemberId(String accountNumber, Long memberId);

    // 은행 내부 계좌(SYSTEM: SYS-CASH, SYS-INTERBANK)인지 확인 (입금·이체 대상 막기용)
    // 계좌 객체를 불러오지 않고 "있다/없다"만 확인 → 뒤의 비관적 락 조회에 옛날 값이 섞이지 않게 함
    boolean existsByAccountNumberAndAccountType(String accountNumber, AccountType accountType);

    // [타행] 계좌 id로 계좌번호 글자만 조회 (환불할 때 사용)
    // → 계좌 객체를 불러오지 않으므로, 뒤에서 비관적 락으로 계좌를 읽을 때 옛날 잔액이 섞이지 않음
    @Query("select a.accountNumber from Account a where a.id = :id")
    Optional<String> findAccountNumberById(@Param("id") Long id);
}