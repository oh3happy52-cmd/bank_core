-- [무엇] 멱등성 키 메모장 테이블 추가 + transfer의 UNIQUE 제거
-- [역할] 번호표(Idempotency-Key)를 이체 기록(transfer)에서 떼어 내 24시간만 보관
--        - 이체 기록(영수증)은 영구 보관
--        - 번호표 메모는 유효기간(expires_at)이 지나면 새 요청으로 보고, 정리 작업이 삭제
-- [누가 쓰나] 서버를 켤 때 Flyway가 한 번 실행

-- 1. 메모장 테이블
CREATE TABLE idempotency_key (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    idempotency_key  VARCHAR(64)  NOT NULL,             -- 번호표
    request_hash     CHAR(64)     NOT NULL,             -- 요청 내용 해시 (같은 번호표에 다른 내용인지 검사)
    transfer_id      BIGINT       NOT NULL,             -- 이 번호표로 만든 이체 기록(영수증) 번호
    created_at       DATETIME(6)  NOT NULL,
    expires_at       DATETIME(6)  NOT NULL,             -- 유효기간. 이 시각이 지나면 새 요청으로 처리
    PRIMARY KEY (id),
    UNIQUE KEY uk_idempotency_key (idempotency_key),    -- 유효한 메모는 번호표당 1개 (동시 중복 요청 차단)
    KEY idx_idempotency_expires_at (expires_at),        -- 정리 작업이 "유효기간 지난 것"을 빨리 찾기 위한 인덱스
    CONSTRAINT fk_idempotency_transfer FOREIGN KEY (transfer_id) REFERENCES transfer (id)
);

-- 2. 기존 번호표를 메모장으로 옮기기 (유효기간 = 요청 시각 + 24시간)
INSERT INTO idempotency_key (idempotency_key, request_hash, transfer_id, created_at, expires_at)
SELECT idempotency_key, request_hash, id, requested_at, DATE_ADD(requested_at, INTERVAL 24 HOUR)
FROM transfer;

-- 3. transfer의 UNIQUE 제거 → 일반 인덱스로 교체
--    (같은 번호표가 24시간 뒤 새 요청으로 다시 쓰일 수 있으므로 영수증 쪽에서는 중복을 막지 않음)
ALTER TABLE transfer
    DROP INDEX uk_transfer_idempotency_key,
    ADD KEY idx_transfer_idempotency_key (idempotency_key);


    