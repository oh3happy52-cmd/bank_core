package com.example.bank_core.interbank.domain;

// [무엇] 타행 이체 한 건 (interbank_transfer 테이블 한 줄)
// [역할] 번호표, 받는 은행·계좌, 금액, 상태(요청됨/완료/모름/실패), 시도 횟수를 기록
//        상태는 정해진 방향으로만 바뀜: REQUESTED → COMPLETED / FAILED / UNKNOWN, UNKNOWN → COMPLETED / FAILED
// [누가 쓰나] InterbankTransferService(요청·결과 처리), 자동 확인 작업, InterbankTransferRepository

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "interbank_transfer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterbankTransfer {

    // 타행 이체 상태
    public enum Status {
        REQUESTED,   // 우리 쪽 출금은 끝났고, 다른 은행에 요청 보내는 중
        COMPLETED,   // 다른 은행이 입금했다고 확인됨
        UNKNOWN,     // 응답이 없어서 입금됐는지 모름 → 자동 확인 작업이 다시 물어봄
        FAILED       // 다른 은행이 확실히 거절함 → 고객에게 환불 완료
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, unique = true, length = 40)
    private String requestId;

    @Column(name = "send_transfer_id", nullable = false)
    private Long sendTransferId;

    @Column(name = "refund_transfer_id")
    private Long refundTransferId;

    @Column(name = "from_account_id", nullable = false)
    private Long fromAccountId;

    @Column(name = "to_bank_code", nullable = false, length = 10)
    private String toBankCode;

    @Column(name = "to_account_number", nullable = false, length = 30)
    private String toAccountNumber;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 10)
    private Status status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "failure_reason", length = 100)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 새 타행 이체 기록 (우리 쪽 출금이 끝난 직후에 만듦)
    public static InterbankTransfer request(String requestId, Long sendTransferId, Long fromAccountId,
                                            String toBankCode, String toAccountNumber, long amount) {
        InterbankTransfer transfer = new InterbankTransfer();
        transfer.requestId = requestId;
        transfer.sendTransferId = sendTransferId;
        transfer.fromAccountId = fromAccountId;
        transfer.toBankCode = toBankCode;
        transfer.toAccountNumber = toAccountNumber;
        transfer.amount = amount;
        transfer.status = Status.REQUESTED;
        transfer.attemptCount = 0;
        return transfer;
    }

    // 다른 은행에 요청 또는 조회를 한 번 보낼 때마다 호출
    public void recordAttempt() {
        this.attemptCount++;
    }

    // 다른 은행이 입금했다고 확인됨
    public void complete() {
        requireNotFinished();
        this.status = Status.COMPLETED;
    }

    // 응답이 없어서 결과를 모름
    public void markUnknown() {
        requireNotFinished();
        this.status = Status.UNKNOWN;
    }

    // 다른 은행이 확실히 거절함 → 환불 이체 번호와 이유를 기록
    public void fail(String reason, Long refundTransferId) {
        requireNotFinished();
        this.status = Status.FAILED;
        this.failureReason = reason;
        this.refundTransferId = refundTransferId;
    }

    public boolean isFinished() {
        return status == Status.COMPLETED || status == Status.FAILED;
    }

    // 이미 끝난(완료·실패) 건은 상태를 바꿀 수 없음 → 완료된 건을 실수로 환불하는 사고 방지
    private void requireNotFinished() {
        if (isFinished()) {
            throw new IllegalStateException("이미 끝난 타행 이체입니다: " + requestId + " (" + status + ")");
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