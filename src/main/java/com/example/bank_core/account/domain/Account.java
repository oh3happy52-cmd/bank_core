package com.example.bank_core.account.domain;

/**
 * [무엇] 계좌 한 개를 나타내는 엔티티 (DB의 account 테이블과 연결)
 * [역할] 잔액을 들고 있고, 입금(credit)·출금(debit) 규칙을 직접 검사
 *        규칙을 어기면 BusinessException을 던져서 GlobalExceptionHandler가 알맞은 오류로 응답하게 함
 * [누가 쓰나] TransferService(입금·이체), AccountService(조회)
 */

import com.example.bank_core.common.exception.BusinessException;
import com.example.bank_core.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @Column(name = "owner_name", nullable = false, length = 50)
    private String ownerName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "account_type", nullable = false, length = 10)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 10)
    private AccountStatus status;

    @Column(name = "balance", nullable = false)
    private long balance;   // 원 단위. 돈은 오차가 생기는 double을 쓰지 않음

    // 낙관적 락용 버전 번호 (UPDATE 시 "WHERE version = 내가 읽은 번호" 조건이 자동으로 붙음)
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 새 계좌 개설 (잔액 0원, 사용 중 상태로 시작)
    public static Account open(String accountNumber, String ownerName, AccountType accountType) {
        Account account = new Account();
        account.accountNumber = accountNumber;
        account.ownerName = ownerName;
        account.accountType = accountType;
        account.status = AccountStatus.ACTIVE;
        account.balance = 0L;
        return account;
    }

    // 입금 (잔액 증가)
    public void credit(long amount) {
        validateAmount(amount);
        validateActive();
        this.balance += amount;
    }

    // 출금 (잔액 감소)
    public void debit(long amount) {
        validateAmount(amount);
        validateActive();
        // 고객 계좌는 잔액보다 많이 출금할 수 없음. 시스템 계좌(은행 금고)는 예외
        if (this.accountType == AccountType.CUSTOMER && this.balance < amount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        this.balance -= amount;
    }

    // 금액은 0보다 커야 함
    private void validateAmount(long amount) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT);
        }
    }

    // 사용 중인 계좌만 거래 가능
    private void validateActive() {
        if (this.status != AccountStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE, this.accountNumber);
        }
    }

    @PrePersist   // 처음 저장되기 직전에 자동 실행
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate    // 수정되어 저장되기 직전에 자동 실행
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}