package com.example.bank_core.transfer.domain;

// [무엇] 이체 기록(transfer 테이블)을 DB에서 읽고 쓰는 창구
// [역할] 저장, 조회 + Idempotency-Key로 기존 이체 찾기
// [누가 쓰나] TransferService

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    // 같은 Idempotency-Key로 이미 처리한 이체가 있는지 찾기 (멱등성 검사용)
    // 메서드 이름만 보고 Spring이 "where idempotency_key = ?" 쿼리를 자동으로 만들어 줌
    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);
}