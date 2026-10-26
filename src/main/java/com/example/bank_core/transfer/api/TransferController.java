package com.example.bank_core.transfer.api;

import com.example.bank_core.transfer.application.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

// @RestController: "이 클래스는 손님을 맞는 창구예요"라고 Spring에 알려 줌
// @RequestMapping: 이 창구의 기본 주소. 아래 주소들 앞에 /api/v1 이 붙음
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;   // 업무 담당 직원

    /**
     * 입금 창구
     * POST /api/v1/accounts/{계좌번호}/deposits
     */
    @PostMapping("/accounts/{accountNumber}/deposits")
    @ResponseStatus(HttpStatus.CREATED)   // 성공하면 201 ("새 기록을 만들었어요")
    public TransferResponse deposit(
            @RequestHeader("Idempotency-Key") String idempotencyKey,   // 요청 번호표 (머리말에 담겨 옴)
            @PathVariable String accountNumber,                        // 주소에 들어 있는 계좌번호
            @Valid @RequestBody DepositRequest request) {              // 본문의 입금 신청 용지 (@Valid: 규칙 검사)
        return TransferResponse.from(
                transferService.deposit(idempotencyKey, accountNumber, request.amount()));
    }

    /**
     * 이체 창구
     * POST /api/v1/transfers
     */
    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return TransferResponse.from(transferService.transfer(
                idempotencyKey,
                request.fromAccountNumber(),
                request.toAccountNumber(),
                request.amount()));
    }
}