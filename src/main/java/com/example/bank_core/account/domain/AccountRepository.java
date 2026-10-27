package com.example.bank_core.account.domain;

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
}