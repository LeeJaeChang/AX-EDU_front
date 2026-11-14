package com.axedu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.axedu.common.security.JwtProperties;
import com.axedu.common.security.JwtTokenProvider;
import com.axedu.config.DemoAccounts;
import com.axedu.config.DemoProperties;
import com.axedu.config.DemoTokenLogger;
import com.axedu.config.LocalSeedData;
import com.axedu.controller.DevAuthController;
import com.axedu.entity.User;
import com.axedu.repository.ParentStudentRepository;
import com.axedu.repository.UserRepository;

/** 시연용 데모 계정 고정 토큰 + 데모 데이터 켜고 끄기 */
class DemoAccountsTest {

    private static final OffsetDateTime FUTURE = OffsetDateTime.parse("2099-12-31T23:59:59+09:00");

    @Test
    void fixedTokensAreDeterministicAndBoundToSecret() {
        JwtTokenProvider a = new JwtTokenProvider(new JwtProperties("demo-secret-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 0));
        JwtTokenProvider b = new JwtTokenProvider(new JwtProperties("demo-secret-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", 0));
        List<DemoAccounts.Token> first = DemoAccounts.tokens(a, FUTURE);
        List<DemoAccounts.Token> again = DemoAccounts.tokens(a, FUTURE);

        assertThat(first).extracting(DemoAccounts.Token::token).containsExactlyElementsOf(again.stream().map(DemoAccounts.Token::token).toList());
        assertThat(first).extracting(t -> t.account().userId()).containsExactly("usr_t1", "usr_p1", "usr_p2");
        for (DemoAccounts.Token t : first) {
            assertThat(a.parseUserId(t.token())).isEqualTo(t.account().userId());
            assertThat(b.parseUserId(t.token())).as("다른 서명 키로는 통과하지 않음").isNull();
        }
        // 만료가 지난 토큰은 거절
        String expired = DemoAccounts.tokens(a, OffsetDateTime.parse("2026-11-02T00:00:00+09:00")).get(0).token();
        assertThat(a.parseUserId(expired)).isNull();
        assertThat(DemoAccounts.asEnvLines(first)).contains("VITE_DEMO_TOKEN_USR_T1=", "VITE_DEMO_TOKEN_USR_P1=", "VITE_DEMO_TOKEN_USR_P2=");
    }

    /** 데모 켜짐 (local 프로필 = 배포에서 AXEDU_DEMO_ENABLED=true 와 같은 상태) */
    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles({"local", "test"})
    class Enabled {

        @Autowired
        MockMvc mvc;

        @Autowired
        JwtTokenProvider tokens;

        @Autowired
        DemoProperties demo;

        @Autowired
        UserRepository users;

        @Autowired
        ParentStudentRepository parentStudents;

        @Test
        void fixedTokensWorkAgainstApiAndMatchSeededAccounts() throws Exception {
            for (DemoAccounts.Token t : DemoAccounts.tokens(tokens, demo.tokenExpiresAt())) {
                DemoAccounts.Account a = t.account();
                User u = users.findById(a.userId()).orElseThrow();
                assertThat(u.getRole().name()).isEqualTo(a.role());
                assertThat(u.getName()).isEqualTo(a.name());
                assertThat(u.getSchoolId()).isEqualTo(LocalSeedData.SCHOOL);
                for (String child : a.childStudentIds()) {
                    assertThat(parentStudents.existsByParentIdAndStudentId(a.userId(), child)).isTrue();
                }
                mvc.perform(get("/api/axedu/v1/me").header("Authorization", "Bearer " + t.token()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.id").value(a.userId()))
                        .andExpect(jsonPath("$.data.role").value(a.role()));
            }
        }
    }

    /** 데모 꺼짐 (배포 기본값): 시드·/dev/token·토큰 로그 없음 */
    @Nested
    @SpringBootTest(properties = "axedu.demo.enabled=false")
    @ActiveProfiles("test")
    class Disabled {

        @Autowired
        ApplicationContext context;

        @Autowired
        UserRepository users;

        @Test
        void noSeedNoDevTokenNoTokenLog() {
            assertThat(users.count()).isZero();
            assertThat(context.getBeanNamesForType(LocalSeedData.class)).isEmpty();
            assertThat(context.getBeanNamesForType(DemoTokenLogger.class)).isEmpty();
            assertThat(context.getBeanNamesForType(DevAuthController.class)).isEmpty();
        }
    }
}
