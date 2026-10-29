package com.example.bank_core.settlement.api;

// [무엇] 일일 정산 API 창구 (운영자용)
// [역할] 날짜를 받아 정산을 수동으로 실행하고 결과를 돌려줌. 날짜를 안 주면 어제
// [누가 쓰나] 운영자가 curl, Swagger 등으로 호출 (자동 정산이 실패했을 때 재실행, 또는 확인용)

import com.example.bank_core.settlement.application.SettlementService;
import com.example.bank_core.settlement.domain.DailySettlement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Tag(name = "운영: 일일 정산", description = "하루치 입금·이체 합계 (매일 00:05 자동 실행, 수동 재실행 가능)")
@RestController
@RequestMapping("/api/v1/admin/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    /**
     * 정산 실행
     * POST /api/v1/admin/settlements?date=2026-10-29
     * 정산 결과를 새로 만드는(저장하는) 작업이므로 POST 사용
     * 이미 정산한 날짜면 새로 만들지 않고 기존 결과를 돌려줌 (멱등)
     */
    @Operation(
            summary = "일일 정산 수동 실행",
            description = """
                    지정한 날짜에 완료된 입금·이체를 합계 내고, 원장 검사 결과와 함께 저장합니다.

                    - 이미 정산한 날짜면 다시 계산하지 않고 기존 결과를 돌려줍니다 (여러 번 실행해도 안전).
                    - 오늘이나 미래 날짜는 아직 끝나지 않았으므로 정산할 수 없습니다.
                    """)
    @ApiResponse(responseCode = "200", description = "정산 완료 (이미 정산된 날짜면 기존 결과)")
    @ApiResponse(responseCode = "400", description = "SETTLEMENT_DATE_NOT_CLOSED: 오늘 또는 미래 날짜")
    @PostMapping
    public SettlementResponse settle(
            @Parameter(description = "정산할 날짜 (YYYY-MM-DD). 비우면 어제", example = "2026-10-28")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {   // 2026-10-29 형식
        LocalDate target = (date != null) ? date : LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        return SettlementResponse.from(settlementService.settle(target));
    }

    // 응답 모양 (엔티티를 그대로 내보내지 않고, 보여 줄 값만 골라 담음)
    public record SettlementResponse(
            LocalDate settlementDate,
            int depositCount,
            long depositAmount,
            int transferCount,
            long transferAmount,
            String ledgerCheck,
            LocalDateTime settledAt
    ) {
        static SettlementResponse from(DailySettlement s) {
            return new SettlementResponse(
                    s.getSettlementDate(),
                    s.getDepositCount(), s.getDepositAmount(),
                    s.getTransferCount(), s.getTransferAmount(),
                    s.getLedgerCheck().name(),
                    s.getSettledAt());
        }
    }
}