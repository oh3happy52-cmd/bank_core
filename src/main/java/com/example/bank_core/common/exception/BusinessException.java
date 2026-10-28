package com.example.bank_core.common.exception;

import lombok.Getter;

// 업무 규칙 위반 오류
// "잔액 부족"처럼 업무 규칙을 어겼을 때 던지는 예외
// 어떤 오류인지(ErrorCode)를 같이 들고 다님
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    // 기본 메시지를 그대로 쓸 때
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    // 메시지에 정보를 덧붙일 때 (예: "계좌를 찾을 수 없습니다: 999-9999")
    public BusinessException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + ": " + detail);
        this.errorCode = errorCode;
    }
}