package com.example.bank_core.member.api;

// [무엇] API 입력 방어 테스트 (공개 배포 서버에서 누구나 Swagger로 시도해 볼 수 있는 요청들)
// [역할] 이상한 요청이 와도 500 오류나 데이터 이상 없이 알맞은 4xx로 막히는지 확인
//        ① 금액 10억 원 초과 → 400            ② 번호표 64자 초과 → 400 INVALID_IDEMPOTENCY_KEY
//        ③ 은행 내부 계좌로 이체·입금 → 422     ④ 없는 계좌 조회: 일반 회원 403(존재 여부 숨김), 관리자 404
//        ⑤ 타행 계좌번호 30자 초과 → 400       ⑥ 없는 아이디로 로그인 → 401 (틀린 비밀번호와 같은 응답)
// [누가 쓰나] ./gradlew test 실행 시 JUnit이 실행 (CI에서도 자동 실행)

import com.example.bank_core.TestcontainersConfiguration;
import com.example.bank_core.account.domain.AccountRepository;
import com.example.bank_core.transfer.application.TransferService;
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
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InputDefenseTest {

    @Autowired WebApplicationContext context;
    @Autowired TransferService transferService;
    @Autowired AccountRepository accountRepository;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void 금액이_10억원을_넘으면_400() throws Exception {
        String token = login("chulsoo", "chulsoo1234");

        mockMvc.perform(post("/api/v1/accounts/100-0001/deposits")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "defense-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000000001}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 번호표가_64자를_넘으면_400_INVALID_IDEMPOTENCY_KEY() throws Exception {
        String token = login("chulsoo", "chulsoo1234");
        long before = balanceOf("100-0001");

        mockMvc.perform(post("/api/v1/accounts/100-0001/deposits")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "k".repeat(65))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"));

        assertThat(balanceOf("100-0001")).isEqualTo(before);   // 입금되지 않음
    }

    @Test
    void 고객이_은행_내부_계좌로_이체하면_422이고_돈은_그대로다() throws Exception {
        transferService.deposit("defense-setup-" + UUID.randomUUID(), "100-0001", 10_000);
        long chulsooBefore = balanceOf("100-0001");
        long interbankBefore = balanceOf("SYS-INTERBANK");
        String token = login("chulsoo", "chulsoo1234");

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "defense-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountNumber\":\"100-0001\",\"toAccountNumber\":\"SYS-INTERBANK\",\"amount\":1000}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("SYSTEM_ACCOUNT_NOT_ALLOWED"));

        assertThat(balanceOf("100-0001")).isEqualTo(chulsooBefore);
        assertThat(balanceOf("SYS-INTERBANK")).isEqualTo(interbankBefore);   // 타행 보관 계좌가 틀어지지 않음
    }

    @Test
    void 관리자도_은행_내부_계좌에는_입금할_수_없다_422() throws Exception {
        String token = login("admin", "admin1234");   // 테스트 환경(prod 아님)의 개발용 관리자 비밀번호

        mockMvc.perform(post("/api/v1/accounts/SYS-CASH/deposits")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "defense-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("SYSTEM_ACCOUNT_NOT_ALLOWED"));
    }

    @Test
    void 없는_계좌를_조회하면_일반_회원은_403_관리자는_404() throws Exception {
        String userToken = login("chulsoo", "chulsoo1234");
        String adminToken = login("admin", "admin1234");

        // 일반 회원: 남의 계좌(100-0002)와 없는 계좌(999-9999)가 같은 응답 → 계좌번호가 존재하는지 알 수 없음
        mockMvc.perform(get("/api/v1/accounts/100-0002").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/accounts/999-9999").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 관리자: 전체를 볼 수 있으니 없는 계좌는 404
        mockMvc.perform(get("/api/v1/accounts/999-9999").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void 타행_계좌번호가_30자를_넘으면_400() throws Exception {
        String token = login("chulsoo", "chulsoo1234");

        mockMvc.perform(post("/api/v1/interbank-transfers")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "defense-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountNumber\":\"100-0001\",\"toBankCode\":\"088\",\"toAccountNumber\":\""
                                + "1".repeat(31) + "\",\"amount\":1000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 없는_아이디로_로그인하면_틀린_비밀번호와_같은_401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"nobody\",\"password\":\"whatever\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
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
