package com.example.bank_core.account.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    // 계좌번호로 조회 (락 없음)
    Optional<Account> findByAccountNumber(String accountNumber);

    // 계좌번호로 조회하면서 비관적 락을 검
    // → 실제 SQL: SELECT ... FROM account WHERE account_number = ? FOR UPDATE
    // → 이 트랜잭션이 끝날 때까지, 다른 트랜잭션은 이 행을 잠그려고 하면 기다려야 함
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);
}