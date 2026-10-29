package com.example.bank_core.common.config;

// [무엇] Swagger(API 문서) 기본 정보 설정
// [역할] API 문서 페이지 맨 위에 보이는 제목, 버전, 설명을 정함
// [누가 쓰나] springdoc이 서버를 켤 때 읽어서 /swagger-ui 페이지를 만듦. 직접 부르는 곳은 없음

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bankCoreOpenApi() {
        return new OpenAPI().info(new Info()
                .title("bank_core API")
                .version("v1")
                .description("""
                        계좌·이체 코어 시스템 API

                        - 입금·이체는 `Idempotency-Key` 헤더가 필요합니다. 같은 키로 다시 보내면 처음 결과를 그대로 돌려줍니다.
                        - 에러 응답은 항상 `{code, message}` 모양입니다.
                        - `/api/v1/admin/**` 는 운영자용 API입니다.
                        """));
    }
}