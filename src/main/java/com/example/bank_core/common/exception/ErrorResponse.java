package com.example.bank_core.common.exception;

// [무엇] 오류 응답 본문(JSON)
// [역할] 모든 오류를 {"code": "...", "message": "..."} 같은 모양으로 내보냄 (code는 프로그램용, message는 사람용)
// [누가 쓰나] GlobalExceptionHandler, SecurityConfig(401/403 응답)

// 모든 오류 응답이 똑같이 이 모양으로 나감
// code: 프로그램이 구분하는 이름, message: 사람이 읽는 설명
public record ErrorResponse(String code, String message) {

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message);
    }
}