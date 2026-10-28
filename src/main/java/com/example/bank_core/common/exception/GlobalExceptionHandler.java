package com.example.bank_core.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 오류를 받아서 안내문으로 바꾸는 곳
// @RestControllerAdvice: 모든 창구(Controller)에서 터진 예외를 여기서 한꺼번에 받아 처리
// → 창구마다 try-catch를 쓰지 않아도 됨
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 업무 규칙 위반 (잔액 부족, 없는 계좌 등)
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        log.info("업무 오류: code={}, message={}", code.name(), e.getMessage());
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, e.getMessage()));
    }

    // 요청 값 검사(@Valid) 실패: 예) amount가 0이거나 비어 있음
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("");
        ErrorCode code = ErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, code.getMessage() + " (" + detail + ")"));
    }

    // 요청 본문(JSON)을 읽을 수 없음: 예) 형식이 깨졌거나 숫자 자리에 글자
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        ErrorCode code = ErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, code.getMessage() + " (요청 본문 형식 오류)"));
    }

    // 필수 헤더가 없음: 예) Idempotency-Key 누락
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        ErrorCode code = "Idempotency-Key".equals(e.getHeaderName())
                ? ErrorCode.MISSING_IDEMPOTENCY_KEY
                : ErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, code.getMessage()));
    }

    // 그 밖의 예상 못 한 오류: 손님에게는 자세한 내용을 숨기고, 서버 로그에만 전체 내용을 남김
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("예상하지 못한 오류", e);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, code.getMessage()));
    }
}