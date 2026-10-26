package com.example.bank_core.transfer.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DepositRequest(
        @NotNull @Positive Long amount   // 꼭 있어야 하고, 0보다 커야 함
) {
}