package com.example.bank_core.member.application;

// [무엇] 로그인 서비스
// [역할] ① 아이디로 회원 찾기 ② 비밀번호를 BCrypt 해시와 비교 ③ 맞으면 JWT 발급
//        아이디가 없거나 비밀번호가 틀리면 같은 오류(401 INVALID_CREDENTIALS)
// [누가 쓰나] AuthController (POST /api/v1/auth/login)

import com.example.bank_core.common.exception.BusinessException;
import com.example.bank_core.common.exception.ErrorCode;
import com.example.bank_core.member.domain.Member;
import com.example.bank_core.member.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;   // BCrypt 비교 도구 (SecurityConfig에서 만든 것)
    private final JwtEncoder jwtEncoder;             // 토큰 만드는 도구 (SecurityConfig에서 만든 것)

    // application.properties의 jwt.expiration-minutes 값 (60분)
    @Value("${jwt.expiration-minutes}")
    private long expirationMinutes;

    // 로그인 결과: 토큰과 유효 시간(초)
    public record LoginResult(String accessToken, long expiresInSeconds) {
    }

    @Transactional(readOnly = true)
    public LoginResult login(String loginId, String rawPassword) {
        // 1~2. 아이디로 회원 찾기 + 비밀번호 비교
        //      아이디 없음과 비밀번호 틀림을 같은 오류로 돌려줌 → "이 아이디는 존재한다"는 정보를 주지 않기 위해
        Member member = memberRepository.findByLoginId(loginId)
                .filter(found -> passwordEncoder.matches(rawPassword, found.getPassword()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        // 3. 토큰 내용 (claims) 만들기
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("bank-core")                            // 누가 발급했나
                .subject(String.valueOf(member.getId()))         // 누구 토큰인가 (회원 번호)
                .claim("loginId", member.getLoginId())
                .claim("role", member.getRole().name())          // USER / ADMIN (권한 판단에 사용)
                .issuedAt(now)                                   // 발급 시각
                .expiresAt(now.plus(expirationMinutes, ChronoUnit.MINUTES))   // 만료 시각
                .build();

        // 4. HS256(비밀 키 방식)으로 서명해서 토큰 문자열 만들기
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return new LoginResult(token, expirationMinutes * 60);
    }
}