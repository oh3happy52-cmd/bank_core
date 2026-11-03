package com.example.bank_core.member.api;

// [무엇] 인증·권한 테스트 (HTTP 요청부터 끝까지)
// [역할] MockMvc로 진짜 HTTP 요청처럼 보내서, 문지기(Spring Security) → Controller → Service 전체를 확인
//        401(누구인지 모름), 403(권한 없음), 위조 토큰 차단, 남의 계좌 이체 시 잔액 변화 없음을 증명
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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
class AuthorizationTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    TransferService transferService;

    @Autowired
    AccountRepository accountRepository;

    MockMvc mockMvc;   // 서버를 켜지 않고 HTTP 요청을 흉내 내는 도구

    @BeforeEach
    void setUp() {
        // springSecurity(): 문지기(Spring Security)를 거치도록 설정 → 실제 서버와 같은 순서로 검사됨
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void 토큰_없이_요청하면_401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/100-0001"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 비밀번호가_틀리면_401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"chulsoo\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 토큰_내용을_고치면_서명이_맞지_않아_401() throws Exception {
        String token = login("chulsoo", "chulsoo1234");
        String forged = changeSubject(token, "1", "2");   // "회원 1번" → "회원 2번(이영희)"로 고침, 서명은 그대로

        mockMvc.perform(get("/api/v1/accounts/100-0002").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 본인_계좌는_조회할_수_있다() throws Exception {
        String token = login("chulsoo", "chulsoo1234");

        mockMvc.perform(get("/api/v1/accounts/100-0001").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("100-0001"));
    }

    @Test
    void 남의_계좌는_조회할_수_없다_403() throws Exception {
        String token = login("chulsoo", "chulsoo1234");

        mockMvc.perform(get("/api/v1/accounts/100-0002").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 남의_계좌에서_이체하면_403이고_잔액은_그대로다() throws Exception {
        // 준비: 이영희 계좌에 돈을 넣어 둠 (잔액 부족 때문에 실패한 것과 구분하기 위해)
        transferService.deposit("auth-test-" + UUID.randomUUID(), "100-0002", 10_000);
        long youngheeBefore = balanceOf("100-0002");
        long chulsooBefore = balanceOf("100-0001");

        String token = login("chulsoo", "chulsoo1234");
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "auth-test-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountNumber\":\"100-0002\",\"toAccountNumber\":\"100-0001\",\"amount\":1000}"))
                .andExpect(status().isForbidden());

        assertThat(balanceOf("100-0002")).isEqualTo(youngheeBefore);   // 이영희 돈 그대로
        assertThat(balanceOf("100-0001")).isEqualTo(chulsooBefore);    // 김철수 돈도 그대로
    }

    @Test
    void 일반_사용자는_관리자_API를_쓸_수_없다_403() throws Exception {
        String token = login("chulsoo", "chulsoo1234");

        mockMvc.perform(get("/api/v1/admin/ledger/verification").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void 관리자는_관리자_API를_쓸_수_있다() throws Exception {
        String token = login("admin", "admin1234");

        mockMvc.perform(get("/api/v1/admin/ledger/verification").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // 로그인해서 응답의 accessToken 값만 꺼냄 (터미널에서 sed로 잘라 TOKEN에 넣던 것과 같은 일)
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

    // 토큰의 2번째 부분(내용)만 풀어서 sub 값을 바꾸고 다시 포장. 3번째 부분(서명)은 원래 것 그대로 붙임
    private String changeSubject(String token, String from, String to) {
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String changed = payload.replace("\"sub\":\"" + from + "\"", "\"sub\":\"" + to + "\"");
        assertThat(changed).isNotEqualTo(payload);   // 실제로 바뀌었는지 확인

        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(changed.getBytes(StandardCharsets.UTF_8));
        return parts[0] + "." + encoded + "." + parts[2];
    }

    private long balanceOf(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).orElseThrow().getBalance();
    }
}