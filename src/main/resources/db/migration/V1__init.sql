-- 계좌
CREATE TABLE account (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    account_number  VARCHAR(20)  NOT NULL,
    owner_name      VARCHAR(50)  NOT NULL,
    account_type    VARCHAR(10)  NOT NULL,             -- CUSTOMER / SYSTEM
    status          VARCHAR(10)  NOT NULL,             -- ACTIVE / FROZEN / CLOSED
    balance         BIGINT       NOT NULL DEFAULT 0,   -- 원 단위
    version         BIGINT       NOT NULL DEFAULT 0,   -- 낙관적 락용
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_number (account_number)
);

-- 이체 요청 (입금/출금/이체 모두 기록)
CREATE TABLE transfer (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    idempotency_key  VARCHAR(64)  NOT NULL,
    request_hash     CHAR(64)     NOT NULL,
    type             VARCHAR(10)  NOT NULL,            -- DEPOSIT / WITHDRAWAL / TRANSFER
    from_account_id  BIGINT       NOT NULL,
    to_account_id    BIGINT       NOT NULL,
    amount           BIGINT       NOT NULL,
    status           VARCHAR(10)  NOT NULL,            -- PENDING / COMPLETED / FAILED
    failure_reason   VARCHAR(100),
    requested_at     DATETIME(6)  NOT NULL,
    completed_at     DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_transfer_idempotency_key (idempotency_key),
    CONSTRAINT chk_transfer_amount CHECK (amount > 0),
    CONSTRAINT fk_transfer_from FOREIGN KEY (from_account_id) REFERENCES account (id),
    CONSTRAINT fk_transfer_to   FOREIGN KEY (to_account_id)   REFERENCES account (id)
);

-- 거래 기록 장부 (이체 1건 = 2줄)
CREATE TABLE ledger_entry (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    transfer_id    BIGINT       NOT NULL,
    account_id     BIGINT       NOT NULL,
    entry_type     VARCHAR(6)   NOT NULL,              -- DEBIT(출금) / CREDIT(입금)
    amount         BIGINT       NOT NULL,
    balance_after  BIGINT       NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ledger_account_created (account_id, created_at),
    CONSTRAINT fk_ledger_transfer FOREIGN KEY (transfer_id) REFERENCES transfer (id),
    CONSTRAINT fk_ledger_account  FOREIGN KEY (account_id)  REFERENCES account (id)
);