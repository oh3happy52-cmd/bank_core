package com.example.bank_core.common.exception;

// [무엇] 오류 목록표 (enum)
// [역할] 이 서비스에서 일어날 수 있는 오류마다 "이름(code), HTTP 상태 코드, 기본 메시지"를 한곳에 정해 둠
// [누가 쓰나] BusinessException을 던지는 모든 곳, GlobalExceptionHandler

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400: 요청 자체가 잘못됨
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다"),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "금액은 0보다 커야 합니다"),
    SAME_ACCOUNT(HttpStatus.BAD_REQUEST, "같은 계좌로는 이체할 수 없습니다"),
    MISSING_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "Idempotency-Key 헤더가 필요합니다"),
    SETTLEMENT_DATE_NOT_CLOSED(HttpStatus.BAD_REQUEST, "아직 끝나지 않은 날짜는 정산할 수 없습니다"),

    // 404: 찾는 대상이 없음
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "계좌를 찾을 수 없습니다"),

    // 409: 같은 키의 요청이 동시에 들어와 충돌함 (먼저 온 요청이 처리 중)
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "같은 요청이 이미 처리 중입니다. 잠시 후 다시 시도해 주세요"),

    // 422: 형식은 맞지만 업무 규칙상 처리할 수 없음
    INSUFFICIENT_BALANCE(HttpStatus.UNPROCESSABLE_ENTITY, "잔액이 부족합니다"),
    ACCOUNT_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "사용할 수 없는 계좌입니다"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_ENTITY, "같은 Idempotency-Key로 다른 내용의 요청을 보낼 수 없습니다"),

    // 500: 예상하지 못한 서버 오류
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다");

    private final HttpStatus status;
    private final String message;
}