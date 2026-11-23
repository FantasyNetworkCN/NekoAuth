package neko.fantasy.auth.service;

import com.jayway.jsonpath.JsonPath;
import neko.fantasy.auth.domain.KnownDevice;
import neko.fantasy.auth.domain.User;
import neko.fantasy.auth.repository.KnownDeviceRepository;
import neko.fantasy.auth.repository.UserRepository;
import neko.fantasy.auth.settings.MailSecurity;
import neko.fantasy.auth.settings.SettingsRequests;
import neko.fantasy.auth.settings.SettingsService;
import neko.fantasy.auth.settings.SettingsService.SmsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:login_risk;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class LoginRiskIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired KnownDeviceRepository knownDevices;
    @Autowired SettingsService settings;
    @MockitoBean EmailVerificationService emailVerification;
    @MockitoBean DypnsSmsClient sms;

    @BeforeEach
    void configureDelivery() {
        settings.update(new SettingsRequests.Update(
                "NekoAuth",
                true,
                List.of("gmail.com"),
                new SettingsRequests.Smtp(true, "smtp.example", 587, "", null, false,
                        MailSecurity.STARTTLS, false, "auth@example.com", "NekoAuth", 10_000, 10_000, 10_000),
                new SettingsRequests.Sms(true, "test-key", "test-secret", false,
                        "test-sign", "test-template", null, null, "min")
        ));
        when(emailVerification.sendLoginStepUpCode(anyString(), anyString()))
                .thenReturn(new EmailVerificationService.CodeDelivery("验证码已发送", 300, 60));
    }

    @Test
    void highRiskLoginUsesSmsStepUpAndTrustsDeviceAfterVerification() throws Exception {
        MvcResult registration = register("sms");
        long userId = ((Number) JsonPath.read(registration.getResponse().getContentAsString(), "$.user.id"))
                .longValue();
        User user = users.findById(userId).orElseThrow();
        user.setPhone("+8613900000999");
        users.saveAndFlush(user);

        MvcResult login = mvc.perform(post("/api/auth/login")
                        .with(remoteAddress("203.0.113.77"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"Password123","device":{
                                  "type":"mobile","typeConfidence":"high","platform":"Android",
                                  "platformConfidence":"high","browser":"Chrome","webdriver":true
                                }}
                                """.formatted(user.getUsername())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("STEP_UP_REQUIRED"))
                .andExpect(jsonPath("$.method").value("sms"))
                .andExpect(jsonPath("$.riskScore").doesNotExist())
                .andReturn();

        String challengeId = JsonPath.read(login.getResponse().getContentAsString(), "$.challengeId");
        var outId = ArgumentCaptor.forClass(String.class);
        var config = new SmsConfig("test-key", "test-secret", "test-sign", null, "code", "min");
        verify(sms).send(eq(config), eq("+8613900000999"), outId.capture(), eq("100001"));
        when(sms.verify(config, "+8613900000999", outId.getValue(), "654321")).thenReturn(true);

        mvc.perform(post("/api/auth/login/challenge/verify")
                        .with(remoteAddress("203.0.113.77"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId":"%s","code":"654321"}
                                """.formatted(challengeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().exists("nekoauth_device"));

        KnownDevice device = knownDevices.findFirstByUserIdOrderByLastSeenAtDesc(userId).orElseThrow();
        assertThat(device.isTrusted(Instant.now())).isTrue();
    }

    @Test
    void highRiskEmailLoginCanBeCompletedWithEmailCode() throws Exception {
        MvcResult registration = register("email");
        String email = JsonPath.read(registration.getResponse().getContentAsString(), "$.user.email");

        MvcResult login = mvc.perform(post("/api/auth/login")
                        .with(remoteAddress("203.0.113.88"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"Password123","device":{
                                  "type":"desktop","typeConfidence":"high","platform":"Windows",
                                  "platformConfidence":"high","browser":"Chrome","webdriver":true
                                }}
                                """.formatted(email)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.method").value("email"))
                .andReturn();

        String challengeId = JsonPath.read(login.getResponse().getContentAsString(), "$.challengeId");
        mvc.perform(post("/api/auth/login/challenge/verify")
                        .with(remoteAddress("203.0.113.88"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challengeId":"%s","code":"123456"}
                                """.formatted(challengeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        verify(emailVerification).verifyLoginStepUpCode(eq(email), eq("123456"), eq(challengeId));
    }

    private MvcResult register(String prefix) throws Exception {
        String username = prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
        return mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@gmail.com","password":"Password123"}
                                """.formatted(username, username)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private static RequestPostProcessor remoteAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
