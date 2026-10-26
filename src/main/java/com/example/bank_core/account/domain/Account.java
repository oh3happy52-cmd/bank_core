package com.example.bank_core.account.domain;

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
    private long balance;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Account open(String accountNumber, String ownerName, AccountType accountType) {
        Account account = new Account();
        account.accountNumber = accountNumber;
        account.ownerName = ownerName;
        account.accountType = accountType;
        account.status = AccountStatus.ACTIVE;
        account.balance = 0L;
        return account;
    }

    // 돈 들어오기 (입금)
    public void credit(long amount) {
        validateAmount(amount);
        validateActive();
        this.balance += amount;
    }

    // 돈 나가기 (출금)
    public void debit(long amount) {
        validateAmount(amount);
        validateActive();
        // 고객 통장은 잔액보다 많이 뺄 수 없음. 은행 금고 통장은 예외
        if (this.accountType == AccountType.CUSTOMER && this.balance < amount) {
            throw new IllegalStateException("잔액이 부족합니다");
        }
        this.balance -= amount;
    }

    private void validateAmount(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("금액은 0보다 커야 합니다");
        }
    }

    private void validateActive() {
        if (this.status != AccountStatus.ACTIVE) {
            throw new IllegalStateException("사용할 수 없는 계좌입니다");
        }
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}