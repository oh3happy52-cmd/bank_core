package com.example.bank_core.interbank.domain;

// [무엇] 타행 이체 기록(interbank_transfer 테이블)을 DB에서 읽고 쓰는 창구
// [역할] 저장, 잠그면서 조회(결과 반영할 때), 보낸 이체 기록으로 찾기(멱등성), 자동 확인 대상 찾기
// [누가 쓰나] InterbankTransferService

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InterbankTransferRepository extends JpaRepository<InterbankTransfer, Long> {

    // 결과를 반영할 때 이 행을 잠금 → 요청 흐름과 자동 확인 작업이 같은 건을 동시에 바꾸지 못하게 함
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from InterbankTransfer t where t.id = :id")
    Optional<InterbankTransfer> findByIdForUpdate(@Param("id") Long id);

    // 같은 Idempotency-Key로 다시 온 요청인지 확인할 때: 고객 계좌 → 보관 계좌 이체 기록 번호로 찾기
    Optional<InterbankTransfer> findBySendTransferId(Long sendTransferId);

    /**
     * 자동 확인 대상 찾기
     * - UNKNOWN: 응답이 없어서 결과를 모르는 건
     * - 오래된 REQUESTED: 출금 후 다른 은행에 요청하기 전·도중에 서버가 멈춘 건 (그대로 두면 영원히 REQUESTED)
     */
    @Query("""
            select t from InterbankTransfer t
            where t.status = com.example.bank_core.interbank.domain.InterbankTransfer.Status.UNKNOWN
               or (t.status = com.example.bank_core.interbank.domain.InterbankTransfer.Status.REQUESTED
                   and t.createdAt < :staleBefore)
            order by t.id
            """)
    List<InterbankTransfer> findReconcileTargets(@Param("staleBefore") LocalDateTime staleBefore);
}