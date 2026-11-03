package com.example.bank_core.common.config;

// [무엇] Swagger(API 문서) 기본 정보 설정
// [역할] API 문서 페이지 맨 위의 제목, 버전, 설명을 정하고,
//        "Authorize" 버튼을 만들어 토큰을 한 번 넣으면 모든 API 요청에 자동으로 붙게 함
// [누가 쓰나] springdoc이 서버를 켤 때 읽어서 /swagger-ui 페이지를 만듦. 직접 부르는 곳은 없음

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI bankCoreOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("bank_core API")
                        .version("v1")
                        .description("""
                                계좌·이체 코어 시스템 API

                                - **사용 방법**: `인증 > 로그인`으로 토큰을 받은 뒤, 오른쪽 위 **Authorize** 버튼에 토큰을 넣으세요.
                                  (개발용 계정: `chulsoo / chulsoo1234`, `younghee / younghee1234`, 관리자 `admin / admin1234`)
                                - 입금·이체는 `Idempotency-Key` 헤더가 필요합니다. 같은 키로 다시 보내면 처음 결과를 그대로 돌려줍니다.
                                - 에러 응답은 항상 `{code, message}` 모양입니다. 토큰 없음은 401, 권한 없음은 403입니다.
                                - `/api/v1/admin/**` 는 관리자(ADMIN)만 사용할 수 있습니다.
                                """))
                // "Bearer 토큰" 방식의 인증을 쓴다고 알려 줌 → Swagger 화면에 Authorize 버튼이 생김
                .components(new Components().addSecuritySchemes(BEARER_AUTH,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                // 모든 API에 이 인증을 적용 → Authorize에 넣은 토큰이 요청마다 자동으로 붙음
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}