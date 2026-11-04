package com.example.bank_core.interbank.client;

// [무엇] 다른 은행 API를 부르는 HTTP 클라이언트
// [역할] 입금 요청과 결과 조회를 보내고, 응답을 4가지 결과 중 하나로 정리
//        - SUCCESS: 입금됨 (200)
//        - REJECTED: 확실히 거절됨 (422, 예: 없는 계좌)
//        - NOT_FOUND: 다른 은행이 그 번호표를 받은 적 없음 (조회 결과 404)
//        - UNKNOWN: 타임아웃, 연결 실패, 500 등 → 입금됐는지 모름
// [누가 쓰나] InterbankTransferService

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Slf4j
@Component
public class InterbankClient {

    // 다른 은행의 응답 결과 4가지
    public enum Outcome {
        SUCCESS, REJECTED, NOT_FOUND, UNKNOWN
    }

    // 다른 은행에 보내는 입금 요청 / 다른 은행의 응답 모양
    record DepositRequest(String requestId, String accountNumber, long amount) {
    }

    record DepositResponse(String requestId, String result) {
    }

    private final RestClient restClient;
    private final Environment environment;

    public InterbankClient(Environment environment,
                           @Value("${interbank.timeout-millis}") long timeoutMillis) {
        // 연결과 응답 읽기 모두 timeoutMillis 안에 끝나지 않으면 예외 → UNKNOWN으로 처리
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMillis));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMillis));

        // RestClient.builder(): Spring Boot의 자동 준비 없이 직접 만듦
        // (Spring Boot 4는 RestClient 자동 준비가 별도 라이브러리로 분리돼 있어서, 추가 라이브러리 없이 직접 생성)
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        this.environment = environment;
    }

    /**
     * 입금 요청: "이 번호표로 이 계좌에 입금해 줘"
     * 같은 번호표로 여러 번 보내도 다른 은행은 한 번만 입금함 (멱등성)
     */
    public Outcome requestDeposit(String requestId, String accountNumber, long amount) {
        try {
            return restClient.post()
                    .uri(baseUrl() + "/deposits")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new DepositRequest(requestId, accountNumber, amount))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 200) return Outcome.SUCCESS;
                        if (status == 422) return Outcome.REJECTED;
                        return Outcome.UNKNOWN;   // 500 등: 처리됐는지 확실하지 않음
                    });
        } catch (ResourceAccessException e) {
            // 타임아웃, 연결 실패: 요청이 도착했는지조차 모름
            log.warn("타행 입금 요청 응답 없음: requestId={}, 이유={}", requestId, e.getMessage());
            return Outcome.UNKNOWN;
        }
    }

    /**
     * 결과 조회: "이 번호표 처리했어?"
     */
    public Outcome queryDeposit(String requestId) {
        try {
            return restClient.get()
                    .uri(baseUrl() + "/deposits/{requestId}", requestId)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 404) return Outcome.NOT_FOUND;
                        if (status != 200) return Outcome.UNKNOWN;
                        DepositResponse body = response.bodyTo(DepositResponse.class);
                        if (body == null) return Outcome.UNKNOWN;
                        return switch (body.result()) {
                            case "SUCCESS" -> Outcome.SUCCESS;
                            case "REJECTED" -> Outcome.REJECTED;
                            default -> Outcome.UNKNOWN;
                        };
                    });
        } catch (ResourceAccessException e) {
            log.warn("타행 결과 조회 응답 없음: requestId={}, 이유={}", requestId, e.getMessage());
            return Outcome.UNKNOWN;
        }
    }

    /**
     * 다른 은행 주소. 지금은 같은 서버 안의 모의 타행(/mock-bank)
     * 테스트는 서버를 임의의 포트로 켜므로, 요청할 때마다 실제 포트를 확인해서 주소를 만듦
     * (local.server.port: 서버가 켜진 뒤 실제 포트, 없으면 server.port, 그것도 없으면 8080)
     */
    private String baseUrl() {
        String port = environment.getProperty("local.server.port",
                environment.getProperty("server.port", "8080"));
        return "http://localhost:" + port + "/mock-bank";
    }
}