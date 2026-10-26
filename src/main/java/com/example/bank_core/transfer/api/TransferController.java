package com.example.bank_core.transfer.api;

import com.example.bank_core.transfer.application.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    // POST /api/v1/accounts/100-0001/deposits → 입금
    @PostMapping("/accounts/{accountNumber}/deposits")
    @ResponseStatus(HttpStatus.CREATED)   // 성공하면 201 (새 기록이 만들어졌다는 뜻)
    public TransferResponse deposit(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable String accountNumber,
            @Valid @RequestBody DepositRequest request) {
        return TransferResponse.from(
                transferService.deposit(idempotencyKey, accountNumber, request.amount()));
    }
}