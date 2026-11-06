package com.example.bank_core.transfer.domain;

// [무엇] 이체 기록(영수증) (transfer 테이블의 한 줄)
// [역할] 입금·이체·타행·환불 한 건마다 종류, 보낸·받은 계좌, 금액, 상태(PENDING→COMPLETED), 시각을 기록
//        돈이 움직인 기록이므로 영구 보관 (번호표 메모는 IdempotencyKey가 24시간만 보관)
// [누가 쓰나] TransferService(작성), SettlementService(정산 합계), InterbankTransferService(타행)

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

    // 이 이체를 만든 번호표 (기록용). V7부터 UNIQUE 아님: 24시간이 지나면 같은 번호표로 새 이체가 생길 수 있음
    @Column(name = "idempotency_key", nullable = false, length = 64)
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