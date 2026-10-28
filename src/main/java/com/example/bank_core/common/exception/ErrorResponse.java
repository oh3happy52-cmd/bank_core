package com.example.bank_core.common.exception;
// 오류 안내문 모양

// 모든 오류 응답이 똑같이 이 모양으로 나감
// code: 프로그램이 구분하는 이름, message: 사람이 읽는 설명
public record ErrorResponse(String code, String message) {

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message);
    }
}