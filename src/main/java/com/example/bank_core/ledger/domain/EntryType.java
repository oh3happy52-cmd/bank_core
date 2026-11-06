package com.example.bank_core.ledger.domain;

// [무엇] 원장 기록 종류
// [역할] DEBIT(돈 나감) / CREDIT(돈 들어옴) 두 가지로 구분
// [누가 쓰나] LedgerEntry, TransferService

public enum EntryType {
    DEBIT,   // 돈 나감
    CREDIT   // 돈 들어옴
}