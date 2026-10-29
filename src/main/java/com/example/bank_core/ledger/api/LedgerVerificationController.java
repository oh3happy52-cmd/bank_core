package com.example.bank_core.ledger.api;

// [무엇] 원장 정합성 검사 API 창구
// [역할] GET 요청이 오면 정합성 검사를 실행하고 결과를 그대로 돌려줌
// [누가 쓰나] 운영자(관리자)가 curl, 브라우저, Swagger로 호출

import com.example.bank_core.ledger.application.LedgerVerificationResult;
import com.example.bank_core.ledger.application.LedgerVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "운영: 원장 검사", description = "잔액과 원장(거래 기록 장부)이 맞는지 검사")
@RestController
@RequestMapping("/api/v1/admin/ledger")   // admin: 고객용이 아니라 운영자용 주소라는 표시
@RequiredArgsConstructor
public class LedgerVerificationController {

    private final LedgerVerificationService ledgerVerificationService;

    /**
     * 원장 정합성 검사
     * GET /api/v1/admin/ledger/verification
     * 데이터를 바꾸지 않고 읽기만 하므로 GET 사용
     */
    @Operation(
            summary = "원장 정합성 검사",
            description = """
                    3가지를 검사하고, 모두 통과하면 consistent=true 입니다.

                    1. 계좌마다 잔액 = 장부 합계 (들어온 돈 - 나간 돈)
                    2. 전체 계좌 잔액의 합계 = 0 (시스템 계좌가 내준 만큼 마이너스)
                    3. 완료된 이체마다 장부가 2줄이고, 나간 돈 = 들어온 돈
                    """)
    @ApiResponse(responseCode = "200", description = "검사 완료 (불일치가 있어도 200, 결과는 consistent 값으로 확인)")
    @GetMapping("/verification")
    public LedgerVerificationResult verify() {
        return ledgerVerificationService.verify();
    }
}