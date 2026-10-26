package com.example.bank_core.transfer.api;

import com.example.bank_core.transfer.domain.Transfer;

public record TransferResponse(
        Long transferId,
        String type,
        long amount,
        String status
) {
    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getType().name(),
                transfer.getAmount(),
                transfer.getStatus().name()
        );
    }
}