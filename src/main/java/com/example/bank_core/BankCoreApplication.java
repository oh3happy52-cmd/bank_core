package com.example.bank_core;

// [무엇] 앱 시작점 (main 메서드)
// [역할] 기본 시간대를 한국 시간으로 정한 뒤 Spring 앱을 켬
// [누가 쓰나] ./gradlew bootRun, 또는 배포 후 java -jar 로 실행할 때
// [시간대를 여기서 정하는 이유] DB 연결이 만들어지기 "전"이어야 함.
//   앱이 켜진 뒤에 바꾸면, 먼저 만들어진 DB 연결(UTC)과 앱(한국)의 기준이 달라져 날짜가 하루 밀림 (CI에서 실제로 발생)

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class BankCoreApplication {

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
		SpringApplication.run(BankCoreApplication.class, args);
	}

}