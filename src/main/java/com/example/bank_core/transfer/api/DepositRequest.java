package com.example.bank_core.transfer.api;

// [무엇] 입금 요청 본문(JSON)
// [역할] {"amount": 10000} 형태로 들어온 금액을 담고, 비어 있거나 0 이하면 400으로 막음(@Valid 검사)
// [누가 쓰나] TransferController(입금 API)

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DepositRequest(
        // 꼭 있어야 하고, 0보다 크고, 1회 최대 10억 원 (터무니없이 큰 금액으로 잔액 숫자가 넘치는 것 방지)
        @NotNull @Positive @Max(value = 1_000_000_000L, message = "1회 최대 10억 원까지 가능합니다") Long amount
) {
}