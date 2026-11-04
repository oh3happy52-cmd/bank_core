package com.example.bank_core.interbank.api;

// [무엇] 타행 이체 API 창구
// [역할] 타행 이체 요청을 받아 InterbankTransferService에 넘기고, 결과에 따라 201/200/202로 응답
//        타행 이체 한 건의 현재 상태 조회 (UNKNOWN이었던 건이 나중에 확정됐는지 확인할 때)
// [누가 쓰나] 외부 클라이언트(앱, curl, Swagger). 토큰 필요

import com.example.bank_core.common.security.LoginMember;
import com.example.bank_core.interbank.application.InterbankTransferService;
import com.example.bank_core.interbank.domain.InterbankTransfer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "타행 이체", description = "다른 은행 계좌로 이체. 응답이 없으면 UNKNOWN으로 두고 자동 확인 작업이 나중에 확정")
@RestController
@RequestMapping("/api/v1/interbank-transfers")
@RequiredArgsConstructor
public class InterbankTransferController {

    private final InterbankTransferService interbankTransferService;

    // 타행 이체 요청 모양
    public record InterbankTransferRequest(
            @NotBlank String fromAccountNumber,   // 보내는 우리 은행 계좌 (본인 것)
            @NotBlank String toBankCode,          // 받는 은행 코드
            @NotBlank String toAccountNumber,     // 받는 은행의 계좌번호 (999로 시작하면 모의 타행이 거절)
            @NotNull @Positive Long amount
    ) {
    }

    // 타행 이체 응답 모양
    public record InterbankTransferResponse(
            Long id,
            String requestId,        // 다른 은행에 보낸 번호표
            String status,           // COMPLETED / FAILED / UNKNOWN / REQUESTED
            long amount,
            String toBankCode,
            String toAccountNumber,
            int attemptCount,        // 다른 은행에 요청·조회한 횟수
            String failureReason     // FAILED일 때 이유
    ) {
        static InterbankTransferResponse from(InterbankTransfer t) {
            return new InterbankTransferResponse(
                    t.getId(), t.getRequestId(), t.getStatus().name(), t.getAmount(),
                    t.getToBankCode(), t.getToAccountNumber(), t.getAttemptCount(), t.getFailureReason());
        }
    }

    /**
     * 타행 이체 요청
     * POST /api/v1/interbank-transfers
     */
    @Operation(summary = "타행 이체", description = """
            우리 은행 계좌에서 다른 은행 계좌로 보냅니다. 보내는 계좌의 주인만 가능합니다.

            - 201 COMPLETED: 다른 은행 입금까지 끝남
            - 200 FAILED: 다른 은행이 거절함 → 보낸 돈은 환불됨
            - 202 UNKNOWN: 다른 은행 응답이 없어 확인 중 → 자동 확인 작업이 확정. 조회 API로 결과 확인
            """)
    @ApiResponse(responseCode = "201", description = "COMPLETED")
    @ApiResponse(responseCode = "200", description = "FAILED (환불 완료)")
    @ApiResponse(responseCode = "202", description = "UNKNOWN (확인 중)")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN: 보내는 계좌가 본인 것이 아님")
    @ApiResponse(responseCode = "422", description = "INSUFFICIENT_BALANCE 등")
    @PostMapping
    public ResponseEntity<InterbankTransferResponse> send(
            @Parameter(description = "요청 번호표 (재전송 시 같은 값)", example = "ib-20261104-0001")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InterbankTransferRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        InterbankTransfer result = interbankTransferService.send(
                LoginMember.from(jwt), idempotencyKey,
                request.fromAccountNumber(), request.toBankCode(), request.toAccountNumber(), request.amount());

        HttpStatus status = switch (result.getStatus()) {
            case COMPLETED -> HttpStatus.CREATED;              // 201
            case FAILED -> HttpStatus.OK;                      // 200
            case UNKNOWN, REQUESTED -> HttpStatus.ACCEPTED;    // 202
        };
        return ResponseEntity.status(status).body(InterbankTransferResponse.from(result));
    }

    /**
     * 타행 이체 한 건 조회 (보낸 사람 또는 관리자)
     * GET /api/v1/interbank-transfers/{id}
     */
    @Operation(summary = "타행 이체 조회", description = "UNKNOWN이었던 건이 나중에 COMPLETED 또는 FAILED로 확정됐는지 확인합니다.")
    @GetMapping("/{id}")
    public InterbankTransferResponse get(
            @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return InterbankTransferResponse.from(interbankTransferService.get(LoginMember.from(jwt), id));
    }
}