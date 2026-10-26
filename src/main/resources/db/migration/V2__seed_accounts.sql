-- 테스트용 기본 통장 (잔액은 0원으로 시작, 돈은 입금 기능으로 넣음) now는 지금 시각
INSERT INTO account (account_number, owner_name, account_type, status, balance, version, created_at, updated_at)
VALUES
    ('SYS-CASH', '은행 금고', 'SYSTEM',   'ACTIVE', 0, 0, NOW(6), NOW(6)),
    ('100-0001', '김철수',    'CUSTOMER', 'ACTIVE', 0, 0, NOW(6), NOW(6)),
    ('100-0002', '이영희',    'CUSTOMER', 'ACTIVE', 0, 0, NOW(6), NOW(6));