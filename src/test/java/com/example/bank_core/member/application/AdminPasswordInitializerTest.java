package com.example.bank_core.member.application;

// [무엇] 운영(prod) 프로필의 관리자 비밀번호 설정 테스트
// [역할] prod 프로필 + ADMIN_PASSWORD 값으로 서버를 켰을 때
//        ① 공개된 개발용 비밀번호(admin1234)로는 로그인이 막히고 ② 환경 변수로 넣은 비밀번호로만 로그인되는지 확인
//        (실제 배포 서버와 같은 프로필로 켜서, 운영에서 admin1234가 살아 있지 않다는 것을 증명)
// [누가 쓰나] ./gradlew test 실행 시 JUnit이 실행 (CI에서도 자동 실행)

import com.example.bank_core.TestcontainersConfiguration;
import com.example.bank_core.common.exception.BusinessException;
import com.example.bank_core.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// prod 프로필에 필요한 값(JWT 비밀 키, 관리자 비밀번호)을 테스트용으로 넣음. DB는 Testcontainers가 따로 띄움
@SpringBootTest(properties = {
        "JWT_SECRET=test-only-jwt-secret-key-0123456789abcdef0123456789",
        "ADMIN_PASSWORD=test-admin-password-2026"
})
@ActiveProfiles("prod")
@Import(TestcontainersConfiguration.class)
class AdminPasswordInitializerTest {

    @Autowired
    AuthService authService;

    @Test
    void 운영에서는_공개된_개발용_관리자_비밀번호로_로그인할_수_없다() {
        assertThatThrownBy(() -> authService.login("admin", "admin1234"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void 운영에서는_환경_변수로_넣은_비밀번호로만_관리자_로그인이_된다() {
        AuthService.LoginResult result = authService.login("admin", "test-admin-password-2026");

        assertThat(result.accessToken()).isNotBlank();
    }
}
