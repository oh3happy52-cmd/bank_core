package com.example.bank_core.transfer.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "transfer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", nullable = false, length = 10)
    private TransferType type;

    @Column(name = "from_account_id", nullable = false)
    private Long fromAccountId;

    @Column(name = "to_account_id", nullable = false)
    private Long toAccountId;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 10)
    private TransferStatus status;

    @Column(name = "failure_reason", length = 100)
    private String failureReason;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // 신청서 작성 (처음엔 "처리 중" 상태)
    public static Transfer request(String idempotencyKey, String requestHash, TransferType type,
                                   Long fromAccountId, Long toAccountId, long amount) {
        Transfer transfer = new Transfer();
        transfer.idempotencyKey = idempotencyKey;
        transfer.requestHash = requestHash;
        transfer.type = type;
        transfer.fromAccountId = fromAccountId;
        transfer.toAccountId = toAccountId;
        transfer.amount = amount;
        transfer.status = TransferStatus.PENDING;
        transfer.requestedAt = LocalDateTime.now();
        return transfer;
    }

    // 처리 완료 도장 찍기
    public void complete() {
        this.status = TransferStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }
}