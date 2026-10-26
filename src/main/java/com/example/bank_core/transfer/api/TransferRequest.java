package com.example.bank_core.transfer.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// 손님이 이체할 때 내는 신청 용지
// record: 값만 담는 간단한 상자. 한 번 만들면 내용이 안 바뀜
public record TransferRequest(
        @NotBlank String fromAccountNumber,   // 보내는 계좌번호 (빈칸이면 안 됨)
        @NotBlank String toAccountNumber,     // 받는 계좌번호 (빈칸이면 안 됨)
        @NotNull @Positive Long amount        // 금액 (꼭 있어야 하고, 0보다 커야 함)
) {
}