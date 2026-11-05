package com.example.bank_core.transfer.domain;

// [무엇] 멱등성 키 메모 (idempotency_key 테이블의 한 줄)
// [역할] "이 번호표는 이미 처리했고, 그 결과 영수증은 몇 번"을 유효기간(24시간)과 함께 기억
//        유효기간이 지나면 새 요청으로 보고, 정리 작업이 삭제함
// [누가 쓰나] TransferService(멱등성 검사), IdempotencyKeyCleanupScheduler(정리 작업)

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_key")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // JPA용 빈 생성자. 밖에서는 issue()로만 만들게 막음
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 번호표 (클라이언트가 보낸 Idempotency-Key)
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    // 요청 내용 해시. 같은 번호표에 다른 내용이 오면 422를 내기 위해 저장
    @Column(name = "request_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String requestHash;

    // 이 번호표로 만든 이체 기록(영수증) 번호. 재전송이 오면 이 영수증을 돌려줌
    @Column(name = "transfer_id", nullable = false)
    private Long transferId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 유효기간. 이 시각이 지나면 같은 번호표라도 새 요청으로 처리
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // 메모 작성: 지금 시각 + 보관 시간(ttl, 기본 24시간)을 유효기간으로 정함
    public static IdempotencyKey issue(String idempotencyKey, String requestHash,
                                       Long transferId, Duration ttl) {
        IdempotencyKey key = new IdempotencyKey();
        key.idempotencyKey = idempotencyKey;
        key.requestHash = requestHash;
        key.transferId = transferId;
        key.createdAt = LocalDateTime.now();
        key.expiresAt = key.createdAt.plus(ttl);
        return key;
    }

    // 유효기간이 지났는지 (유효기간 시각과 정확히 같아도 지난 것으로 봄)
    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }
}
