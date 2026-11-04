package com.example.bank_core.mockbank;

// [무엇] 모의 타행(다른 은행)의 API 창구
// [역할] 우리 은행이 HTTP로 부르는 "다른 은행 API"를 흉내 냄
//        - 입금 요청 / 결과 조회 (고장 확률에 따라 일부러 500 오류나 지연 응답)
//        - 실험용: 고장 설정, 장부 요약, 초기화
// [누가 쓰나] 우리 은행의 타행 이체 기능(InterbankClient, 다음 단계), 실험 테스트, curl
//            우리 은행 토큰 없이 접근 (SecurityConfig에서 permitAll)

import com.example.bank_core.mockbank.MockBank.Deposit;
import com.example.bank_core.mockbank.MockBank.Result;
import com.example.bank_core.mockbank.MockBank.Stats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "모의 타행 서버", description = "실험용 '다른 은행'. 실제 서비스에서는 다른 은행 회사의 서버")
@RestController
@RequestMapping("/mock-bank")
@RequiredArgsConstructor
public class MockBankController {

    private final MockBank mockBank;

    // 입금 요청 모양
    public record DepositRequest(String requestId, String accountNumber, long amount) {
    }

    // 응답 모양. result: SUCCESS / REJECTED / NOT_FOUND / ERROR
    public record DepositResponse(String requestId, String result) {
    }

    // 고장 설정 모양 (예: failRate 0.3 = 30%)
    public record FaultRequest(double failRate, double delayRate, long delayMillis) {
    }

    /**
     * 입금 요청 받기
     * - 실패 고장: 장부에 기록하지 않고 500
     * - 정상 처리 후, 지연 고장이면 응답을 늦게 보냄 (우리 은행 쪽에서는 타임아웃 → 입금됐는지 모름)
     * - 없는 계좌(999로 시작): 422 REJECTED
     */
    @Operation(summary = "입금 요청 (다른 은행 API)")
    @PostMapping("/deposits")
    public ResponseEntity<DepositResponse> deposit(@RequestBody DepositRequest request) throws InterruptedException {
        if (mockBank.shouldFail()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DepositResponse(request.requestId(), "ERROR"));
        }

        Deposit deposit = mockBank.deposit(request.requestId(), request.accountNumber(), request.amount());

        if (mockBank.shouldDelay()) {
            Thread.sleep(mockBank.getDelayMillis());   // 처리는 끝났는데 응답만 늦음
        }

        if (deposit.result() == Result.REJECTED) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(new DepositResponse(request.requestId(), "REJECTED"));
        }
        return ResponseEntity.ok(new DepositResponse(request.requestId(), "SUCCESS"));
    }

    /**
     * 결과 조회: 이 번호표 처리했어?
     * - 실패 고장이면 조회도 500 (다른 은행 서버가 아직 고장 난 상황)
     */
    @Operation(summary = "결과 조회 (다른 은행 API)")
    @GetMapping("/deposits/{requestId}")
    public ResponseEntity<DepositResponse> find(@PathVariable String requestId) {
        if (mockBank.shouldFail()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DepositResponse(requestId, "ERROR"));
        }
        return mockBank.find(requestId)
                .map(deposit -> ResponseEntity.ok(new DepositResponse(requestId, deposit.result().name())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new DepositResponse(requestId, "NOT_FOUND")));
    }

    @Operation(summary = "[실험용] 고장 확률 설정")
    @PutMapping("/faults")
    public Stats configureFaults(@RequestBody FaultRequest request) {
        mockBank.configure(request.failRate(), request.delayRate(), request.delayMillis());
        return mockBank.stats();
    }

    @Operation(summary = "[실험용] 장부 요약")
    @GetMapping("/stats")
    public Stats stats() {
        return mockBank.stats();
    }

    @Operation(summary = "[실험용] 장부 비우기 + 고장 끄기")
    @PostMapping("/reset")
    public Stats reset() {
        mockBank.reset();
        return mockBank.stats();
    }
}