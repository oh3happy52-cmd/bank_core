-- [무엇] 쿼리 튜닝 실험용 데이터 준비
-- [역할] 진짜 bank DB와 분리된 tuning DB를 만들고, transfer 테이블에 가짜 거래 100만 건을 넣음
-- [실행] docker exec -i bank-mysql mysql -uroot -proot < scripts/tuning/01_setup.sql

DROP DATABASE IF EXISTS tuning;
CREATE DATABASE tuning;
USE tuning;

-- bank.transfer와 같은 구조로 만들기 (컬럼과 기존 인덱스는 복사, 외래 키는 복사 안 됨)
CREATE TABLE transfer LIKE bank.transfer;

-- 1부터 100만까지 숫자를 만드는 재귀 쿼리를 쓰기 위해, 재귀 깊이 제한을 100만으로 올림 (기본값 1000)
SET SESSION cte_max_recursion_depth = 1000000;

-- 가짜 거래 100만 건
-- - 기간: 2025-10-30부터 31초 간격 → 약 1년 (하루 약 2,800건)
-- - 종류: 3건 중 1건은 이체, 나머지는 입금
-- - 상태: 20건 중 1건은 실패(FAILED, 완료 시각 없음), 나머지는 완료(COMPLETED)
INSERT INTO transfer (idempotency_key, request_hash, type, from_account_id, to_account_id,
                      amount, status, failure_reason, requested_at, completed_at)
WITH RECURSIVE seq (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 1000000
)
SELECT CONCAT('tuning-', n),
       SHA2(n, 256),
       IF(n % 3 = 0, 'TRANSFER', 'DEPOSIT'),
       1,
       2,
       (n % 100 + 1) * 1000,
       IF(n % 20 = 0, 'FAILED', 'COMPLETED'),
       NULL,
       TIMESTAMP('2025-10-30') + INTERVAL (n * 31) SECOND,
       IF(n % 20 = 0, NULL, TIMESTAMP('2025-10-30') + INTERVAL (n * 31) SECOND)
FROM seq;

-- 결과 확인
SELECT COUNT(*) AS total_rows, MIN(completed_at) AS first_day, MAX(completed_at) AS last_day FROM transfer;