-- 일일 정산 SQL(완료된 거래를 하루 범위로 조회)용 인덱스
-- 같다(=) 조건인 status를 앞에, 범위 조건인 completed_at을 뒤에 둠
-- 실험 결과(100만 건, 하루치 2,647건): 풀 스캔 517ms → 인덱스 범위 스캔 5.87ms
-- 실험 스크립트: scripts/tuning/
CREATE INDEX idx_transfer_status_completed ON transfer (status, completed_at);