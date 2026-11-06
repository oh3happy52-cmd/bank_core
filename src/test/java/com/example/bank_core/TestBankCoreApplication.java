package com.example.bank_core;

// [무엇] 테스트용 앱 실행 진입점 (Testcontainers MySQL로 서버 켜기)
// [역할] 로컬에 MySQL이 없어도, Docker로 테스트용 MySQL을 자동으로 띄운 뒤 서버를 켬
// [누가 쓰나] 개발자가 IDE에서 직접 실행할 때 (./gradlew bootTestRun). 일반 테스트에서는 쓰지 않음

import org.springframework.boot.SpringApplication;

public class TestBankCoreApplication {

	public static void main(String[] args) {
		SpringApplication.from(BankCoreApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
