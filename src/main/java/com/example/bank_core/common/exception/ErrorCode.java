package com.example.bank_core.common.exception;

// [무엇] 오류 목록표 (enum)
// [역할] 이 서비스에서 일어날 수 있는 오류마다 "이름(code), HTTP 상태 코드, 기본 메시지"를 한곳에 정해 둠
// [누가 쓰나] BusinessException을 던지는 모든 곳, GlobalExceptionHandler, 인증·권한 오류 응답

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
    INVALID_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "Idempotency-Key는 1~64자여야 합니다"),
    SETTLEMENT_DATE_NOT_CLOSED(HttpStatus.BAD_REQUEST, "아직 끝나지 않은 날짜는 정산할 수 없습니다"),

    // 401: 누구인지 확인되지 않음 (인증 실패)
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다"),

    // 403: 누구인지는 알지만 권한이 없음 (인가 실패)
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다"),

    // 404: 찾는 대상이 없음
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "계좌를 찾을 수 없습니다"),

    // 409: 같은 키의 요청이 동시에 들어와 충돌함 (먼저 온 요청이 처리 중)
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "같은 요청이 이미 처리 중입니다. 잠시 후 다시 시도해 주세요"),

    // 422: 형식은 맞지만 업무 규칙상 처리할 수 없음
    // (Spring 7부터 422의 이름이 UNPROCESSABLE_ENTITY → UNPROCESSABLE_CONTENT로 바뀜. 숫자는 같은 422)
    INSUFFICIENT_BALANCE(HttpStatus.UNPROCESSABLE_CONTENT, "잔액이 부족합니다"),
    ACCOUNT_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_CONTENT, "사용할 수 없는 계좌입니다"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_CONTENT, "같은 Idempotency-Key로 다른 내용의 요청을 보낼 수 없습니다"),
    SYSTEM_ACCOUNT_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_CONTENT, "은행 내부 계좌로는 입금·이체할 수 없습니다"),

    // 500: 예상하지 못한 서버 오류
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다");

    private final HttpStatus status;
    private final String message;
}