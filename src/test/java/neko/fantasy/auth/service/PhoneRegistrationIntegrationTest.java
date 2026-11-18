package neko.fantasy.auth.service;

import neko.fantasy.auth.repository.SmsRegistrationChallengeRepository;
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
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:phone_registration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PhoneRegistrationIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired SmsRegistrationChallengeRepository challenges;
    @Autowired RegistrationCaptchaService captchas;
    @Autowired SettingsService settings;
    @MockitoBean DypnsSmsClient sms;

    @BeforeEach
    void configureSmsInSettings() {
        settings.update(new SettingsRequests.Update("NekoAuth", true, List.of("gmail.com"),
                new SettingsRequests.Smtp(false, "", 587, "", null, false, MailSecurity.STARTTLS,
                        true, "", "NekoAuth", 10000, 10000, 10000),
                new SettingsRequests.Sms(true, "test-key", "test-secret", false,
                        "test-sign", "test-template", null, null, "min")));
    }

    @Test
    void normalizesPhoneRequiresCodeAndCreatesPasswordLoginWithoutAnEmail() throws Exception {
        String local = "13900000101";
        String canonical = "+86" + local;

        mvc.perform(get("/api/auth/registration-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.smsRegistrationEnabled").value(true));

        register(local, "123456").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SMS_CODE_INVALID"));
        verify(sms, never()).verify(any(SmsConfig.class), anyString(), anyString(), anyString());

        var captcha = captchas.issue();
        send(local, captcha).andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInSeconds").value(360))
                .andExpect(jsonPath("$.resendAfterSeconds").value(60));
        send("13900000102", captcha).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAPTCHA_INVALID"));
        send(canonical, captchas.issue()).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("SMS_CODE_COOLDOWN"));

        var outId = ArgumentCaptor.forClass(String.class);
        var config = new SmsConfig("test-key", "test-secret", "test-sign", null, "code", "min");
        verify(sms).send(eq(config), eq(canonical), outId.capture(), eq("100001"));
        when(sms.verify(config, canonical, outId.getValue(), "123456")).thenReturn(true);
        register(canonical, "123456").andExpect(status().isCreated())
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(jsonPath("$.user.phone").value(canonical))
                .andExpect(jsonPath("$.user.email").doesNotExist());

        assertThat(challenges.findById(canonical).orElseThrow().getOutId()).isNull();
        register(local, "123456").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHONE_TAKEN"));
        for (String identifier : new String[]{local, canonical}) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"identifier":"%s","password":"Password123"}
                                    """.formatted(identifier)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.phone").value(canonical));
        }
    }

    @Test
    void rejectsInvalidPhoneAndLocksOutAfterFiveWrongCodes() throws Exception {
        String phone = "+8613900000103";
        var captcha = captchas.issue();
        send("+12025550123", captcha).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.phone").exists());
        send(phone, captcha).andExpect(status().isOk());

        for (int i = 0; i < 4; i++) {
            register(phone, "111111").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SMS_CODE_INVALID"));
        }
        register(phone, "111111").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SMS_CODE_ATTEMPTS_EXHAUSTED"));
        register(phone, "111111").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SMS_CODE_INVALID"));
        verify(sms, times(5)).verify(any(SmsConfig.class), eq(phone), anyString(), eq("111111"));
        assertThat(users.findByPhone(phone)).isEmpty();
    }

    @Test
    void expiresAtThe360SecondBoundaryAndRejectsBeforeCallingAliyun() throws Exception {
        String phone = "+8613900000105";
        Instant earliestIssuedAt = Instant.now();
        send(phone, captchas.issue()).andExpect(status().isOk());
        Instant latestIssuedAt = Instant.now();

        var challenge = challenges.findById(phone).orElseThrow();
        assertThat(challenge.isActive(earliestIssuedAt.plusSeconds(359))).isTrue();
        assertThat(challenge.isActive(latestIssuedAt.plusSeconds(360))).isFalse();

        Instant issuedAt = Instant.now().minusSeconds(360);
        Instant expiresAt = issuedAt.plusSeconds(360);
        challenge.activate(challenge.getOutId(), expiresAt);
        assertThat(challenge.isActive(expiresAt.minusNanos(1))).isTrue();
        assertThat(challenge.isActive(expiresAt)).isFalse();
        challenges.saveAndFlush(challenge);

        register(phone, "123456").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SMS_CODE_EXPIRED"));
        verify(sms, never()).verify(any(SmsConfig.class), anyString(), anyString(), anyString());
        assertThat(challenges.findById(phone).orElseThrow().getOutId()).isNull();
        assertThat(users.findByPhone(phone)).isEmpty();
    }

    private ResultActions send(String phone, RegistrationCaptchaService.IssuedCaptcha captcha) throws Exception {
        return mvc.perform(post("/api/auth/register/phone/code").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"phone":"%s","captchaId":"%s","captchaAnswer":"%s"}
                        """.formatted(phone, captcha.id(), captcha.answer())));
    }

    private ResultActions register(String phone, String code) throws Exception {
        return mvc.perform(post("/api/auth/register/phone").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"phone":"%s","password":"Password123","verificationCode":"%s"}
                        """.formatted(phone, code)));
    }
}
