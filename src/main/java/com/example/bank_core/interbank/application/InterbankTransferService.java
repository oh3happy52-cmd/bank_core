package com.example.bank_core.interbank.application;

// [무엇] 타행 이체 서비스
// [역할] ① send: 내 계좌 확인 → 보관 계좌로 출금(트랜잭션 ①) → 다른 은행에 요청(트랜잭션 밖) → 결과 반영(트랜잭션 ②)
//        ② reconcileAll: UNKNOWN(과 오래된 REQUESTED)을 다른 은행에 다시 물어봐서 완료 또는 환불로 확정
//        원칙: "확실한 답"을 받기 전에는 완료도 환불도 하지 않음 → 돈이 두 번 나가거나 사라지지 않음
// [누가 쓰나] InterbankTransferController(API), 자동 확인 작업(다음 단계), 실험 테스트

import com.example.bank_core.account.domain.Account;
import com.example.bank_core.account.domain.AccountRepository;
import com.example.bank_core.common.exception.BusinessException;
import com.example.bank_core.common.exception.ErrorCode;
import com.example.bank_core.common.security.LoginMember;
import com.example.bank_core.interbank.client.InterbankClient;
import com.example.bank_core.interbank.client.InterbankClient.Outcome;
import com.example.bank_core.interbank.domain.InterbankTransfer;
import com.example.bank_core.interbank.domain.InterbankTransfer.Status;
import com.example.bank_core.interbank.domain.InterbankTransferRepository;
import com.example.bank_core.transfer.application.TransferService;
import com.example.bank_core.transfer.domain.Transfer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterbankTransferService {

    // 이 횟수 이상 물어봐도 확정이 안 되면 경고 로그 (운영자가 직접 확인하도록)
    private static final int WARN_ATTEMPTS = 10;

    private final TransferService transferService;
    private final AccountRepository accountRepository;
    private final InterbankTransferRepository interbankTransferRepository;
    private final InterbankClient interbankClient;

    // 코드 안에서 "여기부터 여기까지 한 트랜잭션"을 직접 정할 때 쓰는 도구
    // (@Transactional은 메서드 전체가 한 트랜잭션이라, 중간에 외부 요청을 끼워 넣을 수 없어서 이걸 씀)
    private final TransactionTemplate transactionTemplate;

    @Value("${interbank.stale-requested-seconds}")
    private long staleRequestedSeconds;

    // 자동 확인 결과 요약
    public record ReconcileResult(int checked, int completed, int failed, int stillUnknown) {
    }

    /**
     * 타행 이체 보내기
     */
    public InterbankTransfer send(LoginMember loginMember, String idempotencyKey, String fromAccountNumber,
                                  String toBankCode, String toAccountNumber, long amount) {
        // [트랜잭션 ①] 주인 확인 → 보관 계좌로 출금 → 타행 이체 기록(REQUESTED) → 커밋
        InterbankTransfer requested = transactionTemplate.execute(status -> {
            if (!accountRepository.existsByAccountNumberAndMemberId(fromAccountNumber, loginMember.id())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            Transfer send = transferService.sendToInterbank(
                    idempotencyKey, fromAccountNumber, toBankCode + "|" + toAccountNumber, amount);

            // 같은 Idempotency-Key로 다시 온 요청이면, 처음 만든 타행 이체 기록을 그대로 씀
            return interbankTransferRepository.findBySendTransferId(send.getId())
                    .orElseGet(() -> interbankTransferRepository.save(InterbankTransfer.request(
                            "IB-" + UUID.randomUUID(), send.getId(), send.getFromAccountId(),
                            toBankCode, toAccountNumber, amount)));
        });

        // 재요청인데 이미 결과가 정해졌거나(완료·실패) 확인 중(UNKNOWN)이면 다시 보내지 않고 현재 상태를 돌려줌
        if (requested.getStatus() != Status.REQUESTED) {
            return requested;
        }

        // [트랜잭션 밖] 다른 은행에 입금 요청 (최대 interbank.timeout-millis 기다림)
        Outcome outcome = interbankClient.requestDeposit(requested.getRequestId(), toAccountNumber, amount);

        // [트랜잭션 ②] 결과 반영
        return applyOutcome(requested.getId(), outcome);
    }

    /**
     * 자동 확인: UNKNOWN(과 오래된 REQUESTED)을 하나씩 다른 은행에 물어봐서 확정
     */
    public ReconcileResult reconcileAll() {
        LocalDateTime staleBefore = LocalDateTime.now().minusSeconds(staleRequestedSeconds);
        List<InterbankTransfer> targets = interbankTransferRepository.findReconcileTargets(staleBefore);

        int completed = 0;
        int failed = 0;
        int stillUnknown = 0;
        for (InterbankTransfer target : targets) {
            InterbankTransfer result = reconcileOne(target);
            switch (result.getStatus()) {
                case COMPLETED -> completed++;
                case FAILED -> failed++;
                default -> stillUnknown++;
            }
        }
        if (!targets.isEmpty()) {
            log.info("타행 자동 확인: 대상 {}건 → 완료 {}, 실패(환불) {}, 아직 모름 {}",
                    targets.size(), completed, failed, stillUnknown);
        }
        return new ReconcileResult(targets.size(), completed, failed, stillUnknown);
    }

    /**
     * 타행 이체 한 건 조회 (보낸 사람 또는 관리자만)
     * 없거나 남의 것이면 403: 다른 사람의 타행 이체가 있는지조차 알려 주지 않음
     */
    public InterbankTransfer get(LoginMember loginMember, Long id) {
        InterbankTransfer transfer = interbankTransferRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        if (!loginMember.isAdmin()) {
            boolean owner = accountRepository.findById(transfer.getFromAccountId())
                    .map(account -> account.isOwnedBy(loginMember.id()))
                    .orElse(false);
            if (!owner) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }
        return transfer;
    }

    // 한 건 확인
    private InterbankTransfer reconcileOne(InterbankTransfer target) {
        Outcome outcome = interbankClient.queryDeposit(target.getRequestId());

        if (outcome == Outcome.NOT_FOUND) {
            // 다른 은행이 "못 받았다"고 해도 바로 환불하지 않음: 원래 요청이 아직 가는 중일 수 있음
            // → 같은 번호표로 다시 보냄. 원래 요청이 나중에 도착해도 다른 은행은 한 번만 처리하므로 안전
            outcome = interbankClient.requestDeposit(
                    target.getRequestId(), target.getToAccountNumber(), target.getAmount());
        }
        return applyOutcome(target.getId(), outcome);
    }

    /**
     * [트랜잭션 ②] 다른 은행의 답을 반영
     * - 이 행을 잠그고(findByIdForUpdate) 반영 → 요청 흐름과 자동 확인 작업이 같은 건을 동시에 바꾸지 못함
     * - 이미 끝난 건(완료·실패)이면 아무것도 바꾸지 않음
     */
    private InterbankTransfer applyOutcome(Long id, Outcome outcome) {
        return transactionTemplate.execute(status -> {
            InterbankTransfer transfer = interbankTransferRepository.findByIdForUpdate(id).orElseThrow();
            if (transfer.isFinished()) {
                return transfer;
            }
            transfer.recordAttempt();

            switch (outcome) {
                case SUCCESS -> transfer.complete();
                case REJECTED -> refund(transfer, "받는 은행이 거절함 (없는 계좌 등)");
                case NOT_FOUND, UNKNOWN -> {
                    transfer.markUnknown();
                    if (transfer.getAttemptCount() >= WARN_ATTEMPTS) {
                        log.warn("타행 이체 {}회 확인에도 결과 모름: requestId={} → 운영자 확인 필요",
                                transfer.getAttemptCount(), transfer.getRequestId());
                    }
                }
            }
            return transfer;
        });
    }

    // 보관 계좌 → 고객 계좌로 환불하고 실패로 기록 (환불 키를 번호표로 고정 → 두 번 실행돼도 한 번만 환불)
    private void refund(InterbankTransfer transfer, String reason) {
        Transfer refund = transferService.refundFromInterbank(
                "REFUND-" + transfer.getRequestId(), transfer.getFromAccountId(), transfer.getAmount());
        transfer.fail(reason, refund.getId());
    }
}