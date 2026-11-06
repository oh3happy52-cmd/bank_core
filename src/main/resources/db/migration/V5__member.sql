-- [무엇] 회원 테이블 생성 + 계좌에 주인 연결 + 개발용 회원 3명
-- [역할] 로그인(JWT)과 권한(USER/ADMIN) 확인의 기준 데이터를 만듦
-- [누가 쓰나] 서버를 켤 때 Flyway가 한 번 실행. 읽는 쪽은 AuthService(로그인), AccountService(주인 확인)

-- 회원 (로그인과 권한 확인용)
CREATE TABLE member (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    login_id    VARCHAR(30)   NOT NULL,             -- 로그인 아이디
    password    VARCHAR(100)  NOT NULL,             -- BCrypt 해시 (원문은 저장하지 않음)
    name        VARCHAR(50)   NOT NULL,
    role        VARCHAR(10)   NOT NULL,             -- USER / ADMIN
    created_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_login_id (login_id)
);

-- 계좌에 주인(회원) 연결. 시스템 계좌(SYS-CASH)는 주인이 없으므로 NULL 허용
ALTER TABLE account ADD COLUMN member_id BIGINT NULL AFTER id;
ALTER TABLE account ADD CONSTRAINT fk_account_member FOREIGN KEY (member_id) REFERENCES member (id);

-- 개발용 회원 3명 (비밀번호: chulsoo1234 / younghee1234 / admin1234 를 BCrypt로 변환한 값)
-- 실제 서비스에서는 이렇게 비밀번호를 정해 두지 않고, 회원가입으로 만듦
INSERT INTO member (login_id, password, name, role, created_at)
VALUES
    ('chulsoo',  '$2a$10$jr/f6LbIudz/QxCBzfz2Muj17D2QPQpgDrXwXdsCp5fqctuf9Osmm', '김철수', 'USER',  NOW(6)),
    ('younghee', '$2a$10$PdsnEWakBAdauxjfhBy1TuGB5koXb6mR5WYn3xTQtAsZ0J1O7Ngga', '이영희', 'USER',  NOW(6)),
    ('admin',    '$2a$10$amyxFRnMs0pEzGp4/UDhf.84ijsgihChjI8bg4DJiRzJLrKMjYXDy', '관리자', 'ADMIN', NOW(6));

-- 기존 계좌에 주인 연결
UPDATE account a JOIN member m ON m.login_id = 'chulsoo'  SET a.member_id = m.id WHERE a.account_number = '100-0001';
UPDATE account a JOIN member m ON m.login_id = 'younghee' SET a.member_id = m.id WHERE a.account_number = '100-0002';