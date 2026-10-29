package com.example.bank_core.transfer.api;

// [무엇] 입금·이체 API 창구
// [역할] HTTP 요청을 받아 값 검사(@Valid) 후 TransferService에 넘기고, 결과를 응답으로 돌려줌
// [누가 쓰나] 외부 클라이언트(앱, curl, Swagger)

import com.example.bank_core.transfer.application.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

// @RestController: 외부 요청(HTTP)을 받는 창구라고 Spring에 알려 줌
// @RequestMapping: 이 창구의 기본 주소. 아래 주소들 앞에 /api/v1 이 붙음
// @Tag: Swagger 문서에서 이 창구의 API들을 묶어 보여 줄 이름
@Tag(name = "입금·이체", description = "입금과 계좌 간 이체. 모두 Idempotency-Key 헤더가 필요합니다.")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TransferController {

    // Swagger에 보여 줄 Idempotency-Key 설명 (두 API가 같이 씀)
    private static final String IDEMPOTENCY_KEY_DESCRIPTION =
            "요청 번호표. 사용자가 버튼을 누를 때마다 새로 만들고, 응답을 못 받아 다시 보낼 때는 같은 값을 씀";

    private final TransferService transferService;

    /**
     * 입금
     * POST /api/v1/accounts/{계좌번호}/deposits
     */
    @Operation(
            summary = "입금",
            description = """
                    시스템 계좌(SYS-CASH)에서 고객 계좌로 돈을 넣습니다.

                    - 같은 Idempotency-Key + 같은 내용으로 다시 보내면, 돈을 다시 옮기지 않고 처음 결과를 그대로 돌려줍니다.
                    """)
    @ApiResponse(responseCode = "201", description = "입금 완료 (재요청이면 처음 결과)")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST (금액 0 이하 등) / MISSING_IDEMPOTENCY_KEY")
    @ApiResponse(responseCode = "404", description = "ACCOUNT_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "DUPLICATE_REQUEST: 같은 키 요청이 동시에 처리 중. 잠시 후 같은 키로 재시도")
    @ApiResponse(responseCode = "422", description = "ACCOUNT_NOT_ACTIVE / IDEMPOTENCY_KEY_REUSED (같은 키에 다른 내용)")
    @PostMapping("/accounts/{accountNumber}/deposits")
    @ResponseStatus(HttpStatus.CREATED)   // 성공하면 201 (새 기록을 만들었다는 뜻)
    public TransferResponse deposit(
            @Parameter(description = IDEMPOTENCY_KEY_DESCRIPTION, example = "deposit-20261029-0001")
            @RequestHeader("Idempotency-Key") String idempotencyKey,   // 요청 번호표 (헤더로 받음)
            @Parameter(description = "입금받을 계좌번호", example = "100-0001")
            @PathVariable String accountNumber,                        // 주소에 들어 있는 계좌번호
            @Valid @RequestBody DepositRequest request) {              // 본문의 입금 요청 (@Valid: 규칙 검사)
        return TransferResponse.from(
                transferService.deposit(idempotencyKey, accountNumber, request.amount()));
    }

    /**
     * 이체 (비관적 락 사용)
     * POST /api/v1/transfers
     * 2주차 비교 결과로 비관적 락을 최종 방식으로 선택 (docs/week2-summary.md 참고)
     */
    @Operation(
            summary = "이체",
            description = """
                    보내는 계좌에서 받는 계좌로 돈을 옮깁니다.

                    - 동시성 제어: 비관적 락 + 계좌번호 순서대로 잠금 (데드락 방지)
                    - 같은 Idempotency-Key + 같은 내용으로 다시 보내면, 돈을 다시 옮기지 않고 처음 결과를 그대로 돌려줍니다.
                    """)
    @ApiResponse(responseCode = "201", description = "이체 완료 (재요청이면 처음 결과)")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST / SAME_ACCOUNT / MISSING_IDEMPOTENCY_KEY")
    @ApiResponse(responseCode = "404", description = "ACCOUNT_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "DUPLICATE_REQUEST: 같은 키 요청이 동시에 처리 중. 잠시 후 같은 키로 재시도")
    @ApiResponse(responseCode = "422", description = "INSUFFICIENT_BALANCE / ACCOUNT_NOT_ACTIVE / IDEMPOTENCY_KEY_REUSED")
    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(
            @Parameter(description = IDEMPOTENCY_KEY_DESCRIPTION, example = "transfer-20261029-0001")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return TransferResponse.from(transferService.transferWithPessimisticLock(
                idempotencyKey,
                request.fromAccountNumber(),
                request.toAccountNumber(),
                request.amount()));
    }
}