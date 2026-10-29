package com.example.bank_core.ledger.api;

// [무엇] 원장 정합성 검사 API 창구
// [역할] GET 요청이 오면 정합성 검사를 실행하고 결과를 그대로 돌려줌
// [누가 쓰나] 운영자(관리자)가 curl이나 브라우저로 호출

import com.example.bank_core.ledger.application.LedgerVerificationResult;
import com.example.bank_core.ledger.application.LedgerVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    @GetMapping("/verification")
    public LedgerVerificationResult verify() {
        return ledgerVerificationService.verify();
    }
}