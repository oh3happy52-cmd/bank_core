-- [무엇] 타행 이체용 보관 계좌 + 타행 이체 진행 상황 테이블 생성
-- [역할] SYS-INTERBANK(다른 은행에 보낼 돈을 잠시 맡는 계좌)와 interbank_transfer(요청됨/완료/모름/실패 상태 기록)를 만듦
-- [누가 쓰나] 서버를 켤 때 Flyway가 한 번 실행. 읽고 쓰는 쪽은 InterbankTransferService

-- 타행 이체용 보관 계좌: 고객 돈을 다른 은행에 보내기 전에 잠시 맡아 두는 시스템 계좌
-- (입금용 SYS-CASH처럼 SYSTEM 계좌이고 주인은 없음)
INSERT INTO account (account_number, owner_name, account_type, status, balance, version, created_at, updated_at)
VALUES ('SYS-INTERBANK', '타행 이체 보관', 'SYSTEM', 'ACTIVE', 0, 0, NOW(6), NOW(6));

-- 타행 이체 한 건의 진행 상황
CREATE TABLE interbank_transfer (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    request_id          VARCHAR(40)   NOT NULL,           -- 다른 은행에 보내는 번호표 (같은 번호는 한 번만 처리됨)
    send_transfer_id    BIGINT        NOT NULL,           -- 고객 계좌 → 보관 계좌 이체 기록 (transfer.id)
    refund_transfer_id  BIGINT        NULL,               -- 환불했을 때: 보관 계좌 → 고객 계좌 이체 기록
    from_account_id     BIGINT        NOT NULL,           -- 보낸 고객 계좌
    to_bank_code        VARCHAR(10)   NOT NULL,           -- 받는 은행 코드
    to_account_number   VARCHAR(30)   NOT NULL,           -- 받는 은행의 계좌번호
    amount              BIGINT        NOT NULL,
    status              VARCHAR(10)   NOT NULL,           -- REQUESTED / COMPLETED / UNKNOWN / FAILED
    attempt_count       INT           NOT NULL DEFAULT 0, -- 다른 은행에 요청·조회한 횟수
    failure_reason      VARCHAR(100)  NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_interbank_request_id (request_id),
    KEY idx_interbank_status (status),                    -- 자동 확인 작업이 "UNKNOWN인 것"을 자주 찾으므로
    CONSTRAINT fk_interbank_send_transfer   FOREIGN KEY (send_transfer_id)   REFERENCES transfer (id),
    CONSTRAINT fk_interbank_refund_transfer FOREIGN KEY (refund_transfer_id) REFERENCES transfer (id),
    CONSTRAINT fk_interbank_from_account    FOREIGN KEY (from_account_id)    REFERENCES account (id)
);