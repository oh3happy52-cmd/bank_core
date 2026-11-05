package com.example.bank_core.transfer.domain;

// [무엇] 멱등성 키 메모장(idempotency_key 테이블)을 읽고 쓰는 창구
// [역할] 번호표로 메모 찾기 + 유효기간 지난 메모 한꺼번에 삭제
// [누가 쓰나] TransferService(멱등성 검사), IdempotencyKeyCleanupScheduler(정리 작업)

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    // 번호표로 메모 찾기 → "where idempotency_key = ?" 쿼리를 Spring이 자동으로 만들어 줌
    Optional<IdempotencyKey> findByIdempotencyKey(String idempotencyKey);

    /**
     * 유효기간이 지난 메모를 한 번의 DELETE로 삭제하고, 지운 줄 수를 돌려줌
     * - @Modifying: SELECT가 아니라 데이터를 바꾸는 쿼리라고 알려 줌 (부르는 쪽에 트랜잭션 필요)
     * - 한 줄씩 불러와서 지우지 않고 DB에게 "조건에 맞는 것 전부 지워"라고 한 번에 시킴
     */
    @Modifying
    @Query("delete from IdempotencyKey k where k.expiresAt <= :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
