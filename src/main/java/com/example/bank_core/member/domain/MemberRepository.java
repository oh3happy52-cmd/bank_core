package com.example.bank_core.member.domain;

// [무엇] 회원(member 테이블)을 DB에서 읽고 쓰는 창구
// [역할] 로그인 아이디로 회원 찾기
// [누가 쓰나] 로그인 처리(AuthService, 다음 단계)

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    // 로그인 아이디로 회원 찾기 → "where login_id = ?" 쿼리를 Spring이 자동 생성
    Optional<Member> findByLoginId(String loginId);
}