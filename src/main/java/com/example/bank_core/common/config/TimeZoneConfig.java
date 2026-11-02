package com.example.bank_core.common.config;

// [무엇] 앱 기본 시간대 설정
// [역할] 앱이 켜질 때 JVM 기본 시간대를 한국 시간(Asia/Seoul)으로 고정
//        → 서버 컴퓨터가 UTC여도 LocalDateTime.now()(거래 완료 시각 등)가 한국 시간으로 기록됨
// [누가 쓰나] Spring이 앱을 켤 때 자동으로 실행 (테스트 포함). 직접 부르는 곳은 없음
// [왜 필요] 정산은 "어제"를 한국 날짜로 계산하는데, 거래 시각이 UTC로 기록되면
//          한국 시간 00:00~09:00 거래가 전날로 잡혀 정산 날짜가 어긋남 (GitHub Actions 컴퓨터는 UTC)

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class TimeZoneConfig {

    public static final String SEOUL = "Asia/Seoul";

    // @PostConstruct: 이 설정 객체가 만들어진 직후 한 번 실행됨
    @PostConstruct
    public void setDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(SEOUL));
    }
}