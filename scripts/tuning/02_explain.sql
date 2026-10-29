-- [무엇] 정산 SQL의 실행 계획과 실행 시간 확인
-- [역할] 인덱스 추가 "전/후"에 같은 파일을 실행해서 비교
-- [실행] docker exec -i bank-mysql mysql -uroot -proot -t < scripts/tuning/02_explain.sql
-- 대상: 정산 SQL (SettlementService가 쓰는 summarizeCompletedBetween)과 같은 조건, 하루치(2026-06-15)

USE tuning;

-- 1) 실행 계획 (실행은 안 함)
EXPLAIN
SELECT type, COUNT(*) AS tx_count, SUM(amount) AS total_amount
FROM transfer
WHERE status = 'COMPLETED'
  AND completed_at >= '2026-06-15 00:00:00'
  AND completed_at <  '2026-06-16 00:00:00'
GROUP BY type;

-- 2) 실제 실행 + 걸린 시간 (첫 실행은 디스크에서 읽느라 느릴 수 있어서 2번 실행, 두 번째 값으로 비교)
EXPLAIN ANALYZE
SELECT type, COUNT(*) AS tx_count, SUM(amount) AS total_amount
FROM transfer
WHERE status = 'COMPLETED'
  AND completed_at >= '2026-06-15 00:00:00'
  AND completed_at <  '2026-06-16 00:00:00'
GROUP BY type;

EXPLAIN ANALYZE
SELECT type, COUNT(*) AS tx_count, SUM(amount) AS total_amount
FROM transfer
WHERE status = 'COMPLETED'
  AND completed_at >= '2026-06-15 00:00:00'
  AND completed_at <  '2026-06-16 00:00:00'
GROUP BY type;