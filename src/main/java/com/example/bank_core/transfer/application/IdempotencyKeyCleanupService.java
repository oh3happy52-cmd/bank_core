package com.example.bank_core.transfer.application;

// [무엇] 번호표 메모 정리 로직
// [역할] 유효기간이 지난 번호표 메모(idempotency_key)를 DELETE 한 번으로 지우고, 지운 개수를 돌려줌
//        → 메모장이 항상 "최근 24시간치" 크기로 유지됨 (이체 기록 transfer는 건드리지 않음)
// [누가 쓰나] IdempotencyKeyCleanupScheduler(1시간마다 자동 실행), IdempotencyKeyExpiryTest

import com.example.bank_core.transfer.domain.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyKeyCleanupService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    /**
     * 유효기간이 지난 메모 삭제
     * - @Transactional: DELETE 쿼리(@Modifying)는 트랜잭션 안에서만 실행 가능
     * - 알려진 한계: 지울 메모가 수백만 개면 DELETE 한 번이 오래 걸리고 DB에 부담
     *   → 실무에서는 1,000개씩 나눠서 지움. 이 프로젝트 규모에서는 한 번에 지워도 충분
     */
    @Transactional
    public int deleteExpired() {
        int deleted = idempotencyKeyRepository.deleteExpired(LocalDateTime.now());
        log.info("유효기간 지난 번호표 메모 삭제: {}개", deleted);
        return deleted;
    }
}
