package com.example.bank_core;

// [무엇] 스프링 기본 동작 확인 테스트
// [역할] 서버 설정(빈, DB 연결, Flyway 마이그레이션)이 오류 없이 다 켜지는지만 확인
// [누가 쓰나] ./gradlew test 실행 시 JUnit이 실행 (CI에서도 자동 실행)

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BankCoreApplicationTests {

	@Test
	void contextLoads() {
	}

}
