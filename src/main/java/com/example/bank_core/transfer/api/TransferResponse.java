package com.example.bank_core.transfer.api;

// [무엇] 입금·이체 응답 본문(JSON)
// [역할] 이체 기록(Transfer)에서 손님에게 보여 줄 값(번호, 종류, 금액, 상태)만 골라 담음
// [누가 쓰나] TransferController

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