package com.example.bank_core.transfer.application;

// [무엇] 잔액 부족 테스트
// [역할] 잔액보다 많이 보내려고 하면
//        ① 서비스: INSUFFICIENT_BALANCE 예외 + 양쪽 잔액 그대로 + 이체 기록·원장·번호표 메모가 하나도 남지 않음 (전부 롤백)
//        ② API: HTTP 422 + {"code":"INSUFFICIENT_BALANCE"}
//        ③ 타행 이체: 422 + 다른 은행에 요청 자체를 보내지 않음 + 타행 이체 기록도 생기지 않음
//        을 확인. 다른 테스트가 잔액을 바꿔 놓아도 되도록 "지금 잔액 + 1원"을 보내는 방식으로 만듦
// [누가 쓰나] ./gradlew test 실행 시 JUnit이 실행 (CI에서도 자동 실행)

import com.example.bank_core.TestcontainersConfiguration;
import com.example.bank_core.account.domain.AccountRepository;
import com.example.bank_core.common.exception.BusinessException;
import com.example.bank_core.common.exception.ErrorCode;
import com.example.bank_core.common.security.LoginMember;
import com.example.bank_core.interbank.application.InterbankTransferService;
import com.example.bank_core.interbank.domain.InterbankTransferRepository;
import com.example.bank_core.ledger.domain.LedgerEntryRepository;
import com.example.bank_core.mockbank.MockBank;
import com.example.bank_core.transfer.domain.IdempotencyKeyRepository;
import com.example.bank_core.transfer.domain.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransferInsufficientBalanceTest {

    private static final String CHULSOO_ACCOUNT = "100-0001";
    private static final String YOUNGHEE_ACCOUNT = "100-0002";
    private static final LoginMember CHULSOO = new LoginMember(1L, "USER");   // V5에서 넣은 김철수(회원 1번)

    @Autowired WebApplicationContext context;
    @Autowired TransferService transferService;
    @Autowired InterbankTransferService interbankTransferService;
    @Autowired AccountRepository accountRepository;
    @Autowired TransferRepository transferRepository;
    @Autowired LedgerEntryRepository ledgerEntryRepository;
    @Autowired IdempotencyKeyRepository idempotencyKeyRepository;
    @Autowired InterbankTransferRepository interbankTransferRepository;
    @Autowired MockBank mockBank;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        // 김철수 계좌에 돈을 조금 넣어 둠 → "잔액이 0이라서" 실패한 것과 구분
        transferService.deposit("insufficient-setup-" + UUID.randomUUID(), CHULSOO_ACCOUNT, 1_000);
    }

    @Test
    void 잔액보다_많이_이체하면_INSUFFICIENT_BALANCE이고_아무것도_남지_않는다() {
        long chulsooBefore = balanceOf(CHULSOO_ACCOUNT);
        long youngheeBefore = balanceOf(YOUNGHEE_ACCOUNT);
        long transferCountBefore = transferRepository.count();
        long ledgerCountBefore = ledgerEntryRepository.count();
        String key = "insufficient-" + UUID.randomUUID();

        assertThatThrownBy(() -> transferService.transferByMember(
                CHULSOO, key, CHULSOO_ACCOUNT, YOUNGHEE_ACCOUNT, chulsooBefore + 1))   // 잔액 + 1원
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);

        assertThat(balanceOf(CHULSOO_ACCOUNT)).isEqualTo(chulsooBefore);              // 보내는 쪽 그대로
        assertThat(balanceOf(YOUNGHEE_ACCOUNT)).isEqualTo(youngheeBefore);            // 받는 쪽 그대로
        assertThat(transferRepository.count()).isEqualTo(transferCountBefore);         // 이체 기록 안 생김
        assertThat(ledgerEntryRepository.count()).isEqualTo(ledgerCountBefore);       // 원장 안 생김
        assertThat(idempotencyKeyRepository.findByIdempotencyKey(key)).isEmpty();     // 번호표 메모 안 생김 → 같은 키로 다시 시도 가능
    }

    @Test
    void 잔액을_딱_맞게_보내면_성공하고_잔액은_0이_된다() {
        long chulsooBefore = balanceOf(CHULSOO_ACCOUNT);

        transferService.transferByMember(
                CHULSOO, "exact-" + UUID.randomUUID(), CHULSOO_ACCOUNT, YOUNGHEE_ACCOUNT, chulsooBefore);   // 경계값: 잔액과 같은 금액

        assertThat(balanceOf(CHULSOO_ACCOUNT)).isZero();
    }

    @Test
    void API로_잔액보다_많이_이체하면_422와_INSUFFICIENT_BALANCE() throws Exception {
        long chulsooBefore = balanceOf(CHULSOO_ACCOUNT);
        String token = login("chulsoo", "chulsoo1234");

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "insufficient-api-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountNumber\":\"" + CHULSOO_ACCOUNT + "\",\"toAccountNumber\":\"" + YOUNGHEE_ACCOUNT
                                + "\",\"amount\":" + (chulsooBefore + 1) + "}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));

        assertThat(balanceOf(CHULSOO_ACCOUNT)).isEqualTo(chulsooBefore);
    }

    @Test
    void 잔액보다_많이_타행_이체하면_다른_은행에_요청하지_않는다() {
        long chulsooBefore = balanceOf(CHULSOO_ACCOUNT);
        long interbankCountBefore = interbankTransferRepository.count();
        MockBank.Stats bankBefore = mockBank.stats();

        assertThatThrownBy(() -> interbankTransferService.send(
                CHULSOO, "insufficient-ib-" + UUID.randomUUID(), CHULSOO_ACCOUNT, "088", "200-1234", chulsooBefore + 1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);

        MockBank.Stats bankAfter = mockBank.stats();
        assertThat(balanceOf(CHULSOO_ACCOUNT)).isEqualTo(chulsooBefore);                        // 돈 그대로
        assertThat(interbankTransferRepository.count()).isEqualTo(interbankCountBefore);         // 타행 이체 기록 안 생김
        assertThat(bankAfter.successCount()).isEqualTo(bankBefore.successCount());              // 다른 은행 입금 없음
        assertThat(bankAfter.rejectedCount()).isEqualTo(bankBefore.rejectedCount());            // 다른 은행에 요청 자체가 안 감
    }

    private long balanceOf(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).orElseThrow().getBalance();
    }

    private String login(String loginId, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher matcher = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
