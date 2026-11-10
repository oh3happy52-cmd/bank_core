package com.example.bank_core.member.application;

// [무엇] 운영 서버의 관리자 비밀번호 설정 (prod 프로필에서만 동작)
// [역할] 서버가 켜질 때 환경 변수 ADMIN_PASSWORD 값으로 관리자(admin) 비밀번호를 바꿈
//        - 개발용 비밀번호(admin1234)는 V5 마이그레이션에 공개돼 있으므로, 운영 서버에서는 반드시 바꿔야 함
//        - 비밀번호 원문은 서버의 .env 파일에만 있고, 코드·GitHub·DB 어디에도 원문이 남지 않음 (DB에는 BCrypt 해시만)
//        - 값이 없거나 12자보다 짧으면 서버를 켜지 않음 → 약한 비밀번호로 실수 배포하는 것 방지
//        - 이미 같은 비밀번호면 아무것도 하지 않음 → 서버를 여러 번 재시작해도 결과가 같음
// [누가 쓰나] Spring이 서버를 켤 때 자동으로 한 번 실행 (ApplicationRunner). 직접 부르는 곳은 없음

import com.example.bank_core.member.domain.Member;
import com.example.bank_core.member.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile("prod")   // 운영 프로필에서만 이 클래스가 만들어짐 (개발·테스트에서는 admin1234 그대로)
@RequiredArgsConstructor
public class AdminPasswordInitializer implements ApplicationRunner {

    static final String ADMIN_LOGIN_ID = "admin";
    static final int MIN_PASSWORD_LENGTH = 12;

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    // application-prod.properties의 admin.password (= 환경 변수 ADMIN_PASSWORD). 없으면 빈 값
    @Value("${admin.password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminPassword == null || adminPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "운영 서버는 ADMIN_PASSWORD 환경 변수(" + MIN_PASSWORD_LENGTH + "자 이상)가 필요합니다");
        }

        Member admin = memberRepository.findByLoginId(ADMIN_LOGIN_ID)
                .orElseThrow(() -> new IllegalStateException("관리자 계정(admin)이 없습니다"));

        if (passwordEncoder.matches(adminPassword, admin.getPassword())) {
            return;   // 이미 같은 비밀번호 → 바꿀 필요 없음
        }
        admin.changePassword(passwordEncoder.encode(adminPassword));   // 트랜잭션이 끝날 때 DB에 반영
        log.info("관리자 비밀번호를 환경 변수 값으로 설정했습니다");      // 비밀번호 자체는 로그에 남기지 않음
    }
}
