package neko.fantasy.auth.service;

import com.jayway.jsonpath.JsonPath;
import neko.fantasy.auth.domain.User;
import neko.fantasy.auth.exception.ApiException;
import neko.fantasy.auth.repository.SmsRegistrationChallengeRepository;
import neko.fantasy.auth.repository.UserRepository;
import neko.fantasy.auth.settings.MailSecurity;
import neko.fantasy.auth.settings.SettingsRequests;
import neko.fantasy.auth.settings.SettingsService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:phone_password_reset;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PhonePasswordResetIntegrationTest {

    private static final String OLD_PASSWORD = "Password123";
    private static final String NEW_PASSWORD = "NewPassword456";
    private static final String GENERIC_RESPONSE = "如果该手机号已关联账户，验证码将通过短信发送";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired SmsRegistrationChallengeRepository challenges;
    @Autowired UserPasswordEncoder passwords;
    @Autowired RegistrationCaptchaService captchas;
    @Autowired SettingsService settings;
    @Autowired SmsRegistrationService smsVerification;
    @MockitoBean DypnsSmsClient sms;

    @BeforeEach
    void configureSms() {
        configureSms(false, true);
    }

    @Test
    void recoversPhoneOnlyAccountWithPublicRegistrationClosedAndRevokesSessions() throws Exception {
        String phone = "13900000401";
        String canonical = "+86" + phone;
        createAccount("phone_recover", canonical, true);
        var login = login(phone, OLD_PASSWORD).andExpect(status().isOk()).andReturn().getResponse();
        String token = JsonPath.read(login.getContentAsString(), "$.accessToken");

        mvc.perform(get("/api/auth/password/reset-options"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.smsEnabled").value(true));
        var captcha = captchas.issue();
        send(phone, captcha).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(GENERIC_RESPONSE))
                .andExpect(jsonPath("$.expiresInSeconds").value(360))
                .andExpect(jsonPath("$.resendAfterSeconds").value(60));
        send(canonical, captcha).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAPTCHA_INVALID"));
        send(canonical, captchas.issue()).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("SMS_CODE_RATE_LIMIT"));

        var outId = ArgumentCaptor.forClass(String.class);
        verify(sms, timeout(2000)).send(any(), eq(canonical), outId.capture(), eq("100003"));
        var challenge = challenges.findById(canonical).orElseThrow();
        assertThatThrownBy(() -> smsVerification.verifyAndConsume(canonical, "123456"))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("SMS_CODE_INVALID"));
        assertThatThrownBy(
                        () -> smsVerification.verifyBindingCode(canonical, "123456", challenge.getPasswordResetUserId()))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("SMS_CODE_INVALID"));
        verify(sms, never()).verify(any(), anyString(), anyString(), anyString());

        when(sms.verify(any(), eq(canonical), eq(outId.getValue()), eq("123456"))).thenReturn(true);
        reset(canonical, "654321").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SMS_CODE_INVALID"));
        reset(canonical, "123456").andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0));
        assertThat(challenges.findById(canonical).orElseThrow().getOutId()).isNull();
        reset(phone, "123456").andExpect(status().isBadRequest());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(login.getCookie("refresh_token")))
                .andExpect(status().isUnauthorized());
        login(phone, OLD_PASSWORD).andExpect(status().isUnauthorized());
        login(phone, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void missingAndDisabledAccountsGetSameResponseWithoutDeliveryAndKeepOtherPurposeChallenge() throws Exception {
        configureSms(true, true);
        String unknown = "+8613900000402";
        String disabled = "+8613900000403";
        createAccount("disabled_phone", disabled, false);

        smsVerification.send(unknown, "127.0.0.2");
        var registration = challenges.findById(unknown).orElseThrow();
        String registrationOutId = registration.getOutId();
        // Simulate a registration code that's still valid after its resend cooldown.
        registration.reserveSend(Instant.now().minusSeconds(61));
        challenges.saveAndFlush(registration);

        for (String phone : List.of(unknown, disabled)) {
            send(phone, captchas.issue()).andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value(GENERIC_RESPONSE));
            send(phone, captchas.issue()).andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("SMS_CODE_RATE_LIMIT"));
            reset(phone, "123456").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SMS_CODE_INVALID"));
        }
        assertThat(challenges.findById(unknown).orElseThrow().getOutId()).isEqualTo(registrationOutId);
        assertThat(challenges.findById(disabled).orElseThrow().getOutId()).isNull();
        verify(sms).send(any(), eq(unknown), eq(registrationOutId), eq("100001"));
        verify(sms, never()).send(any(), eq(disabled), anyString(), anyString());
        verify(sms, never()).verify(any(), anyString(), anyString(), anyString());
    }

    @Test
    void smsDisabledHidesPhoneRecoveryAndRejectsPhoneDelivery() throws Exception {
        configureSms(false, false);
        mvc.perform(get("/api/auth/password/reset-options"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.smsEnabled").value(false));
        send("13900000404", captchas.issue()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SMS_VERIFICATION_DISABLED"));
        verify(sms, never()).send(any(), anyString(), anyString(), anyString());
    }

    private void createAccount(String username, String phone, boolean enabled) {
        User user = new User();
        user.setUsername(username);
        user.setDisplayName(username);
        user.setPhone(phone);
        user.setEnabled(enabled);
        user.setPasswordHash(passwords.encode(OLD_PASSWORD));
        users.saveAndFlush(user);
    }

    private ResultActions send(String phone, RegistrationCaptchaService.IssuedCaptcha captcha) throws Exception {
        return mvc.perform(post("/api/auth/password/phone/reset-code").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"phone":"%s","captchaId":"%s","captchaAnswer":"%s"}
                        """.formatted(phone, captcha.id(), captcha.answer())));
    }

    private ResultActions reset(String phone, String code) throws Exception {
        return mvc.perform(post("/api/auth/password/phone/reset").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"phone":"%s","code":"%s","newPassword":"%s"}
                        """.formatted(phone, code, NEW_PASSWORD)));
    }

    private ResultActions login(String phone, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"identifier":"%s","password":"%s"}
                        """.formatted(phone, password)));
    }

    private void configureSms(boolean registrationEnabled, boolean smsEnabled) {
        settings.update(new SettingsRequests.Update("NekoAuth", registrationEnabled, List.of("gmail.com"),
                new SettingsRequests.Smtp(false, "", 587, "", null, false, MailSecurity.STARTTLS,
                        false, "", "NekoAuth", 10000, 10000, 10000),
                new SettingsRequests.Sms(smsEnabled, "test-key", "test-secret", false,
                        "test-sign", "test-template", null, null, null)));
    }
}
