package com.example.bank_core.common.security;

// [무엇] 요청한 회원 정보 (토큰에서 꺼낸 회원 번호와 역할)
// [역할] Controller가 토큰(Jwt)에서 만들어 Service에 넘김 → Service가 "계좌 주인인가?"를 판단할 때 사용
// [누가 쓰나] AccountController, TransferController, AccountService, TransferService

import org.springframework.security.oauth2.jwt.Jwt;

public record LoginMember(
        Long id,       // 회원 번호 (토큰의 sub)
        String role    // USER / ADMIN (토큰의 role)
) {

    // 문지기가 검사를 마친 토큰에서 회원 정보 꺼내기
    public static LoginMember from(Jwt jwt) {
        return new LoginMember(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString("role"));
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}