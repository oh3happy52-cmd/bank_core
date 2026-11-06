-- [무엇] 일일 정산 결과 테이블 생성
-- [역할] 하루치 입금·이체 건수와 합계, 원장 검사 결과를 날짜별로 한 줄씩 저장 (같은 날짜는 UNIQUE로 한 줄만)
-- [누가 쓰나] 서버를 켤 때 Flyway가 한 번 실행. 저장은 SettlementService

-- 일일 정산 결과 (하루에 한 줄)
CREATE TABLE daily_settlement (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    settlement_date  DATE         NOT NULL,            -- 정산 대상 날짜
    deposit_count    INT          NOT NULL,            -- 그날 완료된 입금 건수
    deposit_amount   BIGINT       NOT NULL,            -- 그날 입금 합계 (원)
    transfer_count   INT          NOT NULL,            -- 그날 완료된 이체 건수
    transfer_amount  BIGINT       NOT NULL,            -- 그날 이체 합계 (원)
    ledger_check     VARCHAR(10)  NOT NULL,            -- 원장 정합성 검사 결과: PASSED / FAILED
    settled_at       DATETIME(6)  NOT NULL,            -- 정산을 실행한 시각
    PRIMARY KEY (id),
    UNIQUE KEY uk_daily_settlement_date (settlement_date)   -- 같은 날짜는 한 줄만 (중복 정산 방지)
);