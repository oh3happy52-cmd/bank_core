package com.example.bank_core.common.config;

// [무엇] 스케줄링(정해진 시간 자동 실행) 설정
// [역할] @EnableScheduling으로 Spring의 스케줄 기능을 켬. 이게 없으면 @Scheduled가 붙은 메서드가 실행되지 않음
// [누가 쓰나] Spring이 서버를 켤 때 자동으로 읽음. 직접 부르는 곳은 없음

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
}