-- [무엇] 정산 SQL용 인덱스 추가 (실험용 tuning DB)
-- [역할] status(같다 조건) → completed_at(범위 조건) 순서의 복합 인덱스를 만듦
-- [실행] docker exec -i bank-mysql mysql -uroot -proot -t < scripts/tuning/03_add_index.sql
-- 이후 02_explain.sql을 다시 실행해서 전후 비교

USE tuning;

CREATE INDEX idx_transfer_status_completed ON transfer (status, completed_at);

-- 인덱스 목록 확인
SHOW INDEX FROM transfer;