package neko.fantasy.auth;

import neko.fantasy.auth.repository.EmailVerificationCodeRepository;
import neko.fantasy.auth.repository.EmailVerificationSendLimitRepository;
import neko.fantasy.auth.settings.MailSecurity;
import neko.fantasy.auth.settings.MailService;
import neko.fantasy.auth.settings.SettingsService;
import neko.fantasy.auth.service.RegistrationCaptchaService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "auth.registration.require-email-verification=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailVerificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmailVerificationCodeRepository codeRepository;

    @Autowired
    private EmailVerificationSendLimitRepository sendLimitRepository;

    @Autowired
    private RegistrationCaptchaService captchaService;

    @MockitoBean
    private SettingsService settingsService;

    @MockitoBean
    private MailService mailService;

    @Test
    void registrationRequiresCodeDeliveredBySmtpAndConsumesIt() throws Exception {
        String email = uniqueEmail();
        SettingsService.SmtpConfig smtp = smtpConfig();
        when(settingsService.getSmtpConfig()).thenReturn(smtp);
        Captcha captcha = newCaptcha();

        mockMvc.perform(post("/api/auth/register/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(codeRequest(email, captcha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("验证码已发送，请查收邮件"))
                .andExpect(jsonPath("$.expiresInSeconds").value(600))
                .andExpect(jsonPath("$.resendAfterSeconds").value(60));

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendRegistrationCode(
                eq(email), eq(smtp), code.capture(), eq(Duration.ofMinutes(10)));
        assertThat(code.getValue()).matches("\\d{6}");
        assertThat(codeRepository.findById(email)).isPresent();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password123","verificationCode":"%s"}
                                """.formatted(email, code.getValue())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value(email));

        assertThat(codeRepository.findById(email)).isEmpty();
    }

    @Test
    void registrationRejectsWrongCodeWithoutConsumingTheDelivery() throws Exception {
        String email = uniqueEmail();
        when(settingsService.getSmtpConfig()).thenReturn(smtpConfig());
        Captcha captcha = newCaptcha();

        mockMvc.perform(post("/api/auth/register/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(codeRequest(email, captcha)))
                .andExpect(status().isOk());

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendRegistrationCode(
                eq(email), eq(smtpConfig()), code.capture(), eq(Duration.ofMinutes(10)));
        String wrongCode = "000000".equals(code.getValue()) ? "000001" : "000000";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password123","verificationCode":"%s"}
                                """.formatted(email, wrongCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_INVALID"));

        assertThat(codeRepository.findById(email)).isPresent();
    }

    @Test
    void resendWithinCooldownIsRejected() throws Exception {
        String email = uniqueEmail();
        when(settingsService.getSmtpConfig()).thenReturn(smtpConfig());
        Captcha firstCaptcha = newCaptcha();

        mockMvc.perform(post("/api/auth/register/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(codeRequest(email, firstCaptcha)))
                .andExpect(status().isOk());

        codeRepository.deleteById(email);
        Captcha secondCaptcha = newCaptcha();
        mockMvc.perform(post("/api/auth/register/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(codeRequest(email, secondCaptcha)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_COOLDOWN"));

        verify(mailService).sendRegistrationCode(
                eq(email), eq(smtpConfig()), anyString(), eq(Duration.ofMinutes(10)));
    }

    @Test
    void captchaMustBeSolvedAndCanOnlyBeUsedOnce() throws Exception {
        String email = uniqueEmail();
        when(settingsService.getSmtpConfig()).thenReturn(smtpConfig());

        mockMvc.perform(post("/api/auth/register/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.captchaId").exists())
                .andExpect(jsonPath("$.fields.captchaAnswer").exists());

        Captcha captcha = newCaptcha();
        mockMvc.perform(post("/api/auth/register/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(codeRequest(email, captcha)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(codeRequest(email, captcha)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAPTCHA_INVALID"));

        verify(mailService).sendRegistrationCode(
                eq(email), eq(smtpConfig()), anyString(), eq(Duration.ofMinutes(10)));
    }

    @Test
    void captchaEndpointReturnsOneTimeImageChallenge() throws Exception {
        mockMvc.perform(get("/api/auth/register/captcha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.image").value(org.hamcrest.Matchers.startsWith("data:image/svg+xml;base64,")))
                .andExpect(jsonPath("$.expiresInSeconds").value(300));
    }

    @Test
    void emailCanReceiveAtMostFiveCodesPerDay() throws Exception {
        String email = uniqueEmail();
        SettingsService.SmtpConfig smtp = smtpConfig();
        when(settingsService.getSmtpConfig()).thenReturn(smtp);

        for (int index = 0; index < 5; index++) {
            Captcha captcha = newCaptcha();
            mockMvc.perform(post("/api/auth/register/code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(codeRequest(email, captcha)))
                    .andExpect(status().isOk());
            clearCooldown(email);
        }

        Captcha rejectedCaptcha = newCaptcha();
        mockMvc.perform(post("/api/auth/register/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(codeRequest(email, rejectedCaptcha)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_DAILY_LIMIT"));

        verify(mailService, times(5)).sendRegistrationCode(
                eq(email), eq(smtp), anyString(), eq(Duration.ofMinutes(10)));
    }

    private static String uniqueEmail() {
        return "verify_" + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com";
    }

    private Captcha newCaptcha() {
        RegistrationCaptchaService.IssuedCaptcha issued = captchaService.issue();
        return new Captcha(issued.id(), issued.answer());
    }

    private void clearCooldown(String email) {
        var limit = sendLimitRepository.findById(email).orElseThrow();
        limit.setLastSentAt(Instant.now().minusSeconds(61));
        sendLimitRepository.saveAndFlush(limit);
    }

    private static String codeRequest(String email, Captcha captcha) {
        return """
                {"email":"%s","captchaId":"%s","captchaAnswer":"%s"}
                """.formatted(email, captcha.id(), captcha.answer());
    }

    private record Captcha(String id, String answer) {
    }

    private static SettingsService.SmtpConfig smtpConfig() {
        return new SettingsService.SmtpConfig(
                "NekoAuth Test",
                "smtp.example.com",
                587,
                "mailer@example.com",
                "secret",
                MailSecurity.STARTTLS,
                true,
                "noreply@example.com",
                "NekoAuth Test",
                10_000,
                10_000,
                10_000
        );
    }
}
