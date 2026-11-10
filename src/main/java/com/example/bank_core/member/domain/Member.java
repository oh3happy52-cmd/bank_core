package com.example.bank_core.member.domain;

// [무엇] 회원 엔티티 (DB의 member 테이블 한 줄)
// [역할] 로그인 아이디, 비밀번호 해시, 이름, 역할(USER/ADMIN)을 들고 있음
// [누가 쓰나] AuthService(로그인), AdminPasswordInitializer(운영 서버 관리자 비밀번호 설정), MemberRepository

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    // 회원 역할
    public enum Role {
        USER,    // 일반 고객: 내 계좌 조회·이체
        ADMIN    // 관리자: 운영자용 API(/api/v1/admin/**) 사용 가능
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, unique = true, length = 30)
    private String loginId;

    // BCrypt 해시. 원래 비밀번호는 어디에도 저장하지 않음
    @Column(name = "password", nullable = false, length = 100)
    private String password;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role", nullable = false, length = 10)
    private Role role;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 비밀번호 바꾸기: 원래 비밀번호가 아니라 BCrypt로 바꾼 해시를 받아서 저장
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }
}