package neko.fantasy.auth.service;

import com.jayway.jsonpath.JsonPath;
import neko.fantasy.auth.domain.User;
import neko.fantasy.auth.repository.UserRepository;
import neko.fantasy.auth.settings.MailSecurity;
import neko.fantasy.auth.settings.MailService;
import neko.fantasy.auth.settings.SettingsRequests;
import neko.fantasy.auth.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:contact_binding;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "auth.registration.require-email-verification=true"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ContactBindingIntegrationTest {

    private static final String PASSWORD = "Password123";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RegistrationCaptchaService captchas;
    @Autowired SettingsService settings;
    @Autowired UserPasswordEncoder passwords;
    @MockitoBean DypnsSmsClient sms;
    @MockitoBean MailService mail;

    @BeforeEach
    void configureDelivery() {
        configureDelivery(true);
    }

    private void configureDelivery(boolean registrationEnabled) {
        settings.update(new SettingsRequests.Update("NekoAuth", registrationEnabled, List.of("gmail.com"),
                new SettingsRequests.Smtp(true, "smtp.example.test", 587, "", null, false, MailSecurity.STARTTLS,
                        false, "auth@example.test", "NekoAuth", 10000, 10000, 10000),
                new SettingsRequests.Sms(true, "test-key", "test-secret", false,
                        "test-sign", "test-template", null, null, null)));
    }

    @Test
    void bothBindingFlowsRequireLogin() throws Exception {
        for (String kind : List.of("email", "phone")) {
            for (String suffix : List.of("", "/code")) {
                mvc.perform(post("/api/auth/me/" + kind + suffix).contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andExpect(status().isUnauthorized());
            }
        }
    }

    @Test
    void emailCodeIsAccountScopedAndCannotRegisterOrResetPassword() throws Exception {
        String token = createAccount("email_binder", null, "+8613900000301");
        String otherToken = createAccount("email_other", null, "+8613900000302");
        String email = "binding@gmail.com";
        var captcha = captchas.issue();
        send(token, "email", "Binding@Gmail.com", captcha)
                .andExpect(status().isOk()).andExpect(jsonPath("$.resendAfterSeconds").value(60));
        send(token, "email", email, captcha)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CAPTCHA_INVALID"));

        var code = ArgumentCaptor.forClass(String.class);
        verify(mail).sendBindingCode(eq(email), any(SettingsService.SmtpConfig.class), code.capture(), any(Duration.class));
        bind(otherToken, "email", email, code.getValue(), PASSWORD)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_INVALID"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"Password123","verificationCode":"%s"}
                """.formatted(email, code.getValue())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_INVALID"));
        mvc.perform(post("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","code":"%s","newPassword":"NewPassword123"}
                """.formatted(email, code.getValue())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_INVALID"));

        bind(token, "email", email, code.getValue(), "WrongPassword123")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
        bind(token, "email", "Binding@Gmail.com", code.getValue(), PASSWORD)
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
        login(email).andExpect(status().isOk()).andExpect(jsonPath("$.user.username").value("email_binder"));
        bind(token, "email", "replacement@gmail.com", code.getValue(), PASSWORD)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_BOUND"));
        send(otherToken, "email", email, captchas.issue())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void phoneBindingNormalizesAndIsAccountScopedAndSeparateFromRegistration() throws Exception {
        String token = createAccount("phone_binder", "phone-binder@gmail.com", null);
        String otherToken = createAccount("phone_other", "phone-other@gmail.com", null);
        String phone = "13900000303";
        String canonical = "+86" + phone;
        send(token, "phone", phone, captchas.issue())
                .andExpect(status().isOk()).andExpect(jsonPath("$.resendAfterSeconds").value(60));
        send(token, "phone", canonical, captchas.issue())
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("SMS_CODE_COOLDOWN"));
        var outId = ArgumentCaptor.forClass(String.class);
        verify(sms).send(any(SettingsService.SmsConfig.class), eq(canonical), outId.capture());

        bind(otherToken, "phone", canonical, "123456", PASSWORD)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SMS_CODE_INVALID"));
        mvc.perform(post("/api/auth/register/phone").contentType(MediaType.APPLICATION_JSON).content("""
                {"phone":"%s","password":"Password123","verificationCode":"123456"}
                """.formatted(phone)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SMS_CODE_INVALID"));
        verify(sms, never()).verify(any(), anyString(), anyString(), anyString());

        // Closing public registration does not prevent existing accounts from verifying contacts.
        configureDelivery(false);
        when(sms.verify(any(), eq(canonical), eq(outId.getValue()), eq("123456"))).thenReturn(true);
        bind(token, "phone", phone, "654321", PASSWORD)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SMS_CODE_INVALID"));
        bind(token, "phone", canonical, "123456", PASSWORD)
                .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value(canonical));
        login(phone).andExpect(status().isOk()).andExpect(jsonPath("$.user.username").value("phone_binder"));
        bind(token, "phone", "13900000304", "123456", PASSWORD)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PHONE_ALREADY_BOUND"));
        send(otherToken, "phone", phone, captchas.issue())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PHONE_TAKEN"));
    }

    private String createAccount(String username, String email, String phone) throws Exception {
        User user = new User();
        user.setUsername(username);
        user.setDisplayName(username);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwords.encode(PASSWORD));
        users.saveAndFlush(user);
        return "Bearer " + JsonPath.<String>read(
                login(username).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private ResultActions login(String identifier) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"identifier":"%s","password":"%s"}
                """.formatted(identifier, PASSWORD)));
    }

    private ResultActions send(String bearer, String kind, String contact,
                               RegistrationCaptchaService.IssuedCaptcha captcha) throws Exception {
        return mvc.perform(post("/api/auth/me/" + kind + "/code").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"%s":"%s","captchaId":"%s","captchaAnswer":"%s"}
                        """.formatted(kind, contact, captcha.id(), captcha.answer())));
    }

    private ResultActions bind(String bearer, String kind, String contact, String code, String password) throws Exception {
        return mvc.perform(post("/api/auth/me/" + kind).header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"%s":"%s","verificationCode":"%s","currentPassword":"%s"}
                        """.formatted(kind, contact, code, password)));
    }
}
