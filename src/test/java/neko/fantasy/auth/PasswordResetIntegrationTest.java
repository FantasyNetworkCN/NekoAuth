package neko.fantasy.auth;

import neko.fantasy.auth.domain.User;
import neko.fantasy.auth.repository.EmailVerificationCodeRepository;
import neko.fantasy.auth.repository.UserRepository;
import neko.fantasy.auth.service.RegistrationCaptchaService;
import neko.fantasy.auth.service.UserPasswordEncoder;
import neko.fantasy.auth.settings.MailSecurity;
import neko.fantasy.auth.settings.MailService;
import neko.fantasy.auth.settings.SettingsService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserPasswordEncoder passwordEncoder;

    @Autowired
    private RegistrationCaptchaService captchaService;

    @Autowired
    private EmailVerificationCodeRepository codeRepository;

    @MockitoBean
    private SettingsService settingsService;

    @MockitoBean
    private MailService mailService;

    @Test
    void resetCodeChangesPasswordAndConsumesCode() throws Exception {
        User user = createUser();
        when(settingsService.getSmtpConfig()).thenReturn(smtpConfig());
        Captcha captcha = newCaptcha();

        mockMvc.perform(post("/api/auth/password/reset-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(codeRequest(user.getEmail(), captcha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "如果该邮箱已注册，我们已发送重置密码验证码，请查收邮件"))
                .andExpect(jsonPath("$.expiresInSeconds").value(600))
                .andExpect(jsonPath("$.resendAfterSeconds").value(60));

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailService, timeout(2_000)).sendPasswordResetCode(
                eq(user.getEmail()), eq(smtpConfig()), code.capture(), eq(Duration.ofMinutes(10)));
        assertThat(code.getValue()).matches("\\d{6}");
        assertThat(codeRepository.findById(user.getEmail()).orElseThrow().getPurpose().name())
                .isEqualTo("PASSWORD_RESET");

        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetRequest(user.getEmail(), code.getValue(), "NewPassword456")))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0));

        assertThat(passwordEncoder.matches(
                "NewPassword456", userRepository.findById(user.getId()).orElseThrow().getPasswordHash()))
                .isTrue();
        assertThat(codeRepository.findById(user.getEmail())).isEmpty();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest(user.getUsername(), "Password123")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest(user.getUsername(), "NewPassword456")))
                .andExpect(status().isOk());
    }

    @Test
    void resetCodeResponseDoesNotRevealWhetherEmailExists() throws Exception {
        when(settingsService.getSmtpConfig()).thenReturn(smtpConfig());
        String email = "missing_" + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com";
        Captcha captcha = newCaptcha();

        mockMvc.perform(post("/api/auth/password/reset-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(codeRequest(email, captcha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "如果该邮箱已注册，我们已发送重置密码验证码，请查收邮件"));

        verify(mailService, never()).sendPasswordResetCode(
                anyString(), any(SettingsService.SmtpConfig.class), anyString(), any(Duration.class));

        Captcha nextCaptcha = newCaptcha();
        mockMvc.perform(post("/api/auth/password/reset-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(codeRequest(email, nextCaptcha)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_COOLDOWN"));
    }

    private User createUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername("reset_" + suffix);
        user.setEmail("reset_" + suffix + "@gmail.com");
        user.setDisplayName("reset_" + suffix);
        user.setPasswordHash(passwordEncoder.encode("Password123"));
        return userRepository.saveAndFlush(user);
    }

    private Captcha newCaptcha() {
        RegistrationCaptchaService.IssuedCaptcha issued = captchaService.issue();
        return new Captcha(issued.id(), issued.answer());
    }

    private static String codeRequest(String email, Captcha captcha) {
        return """
                {"email":"%s","captchaId":"%s","captchaAnswer":"%s"}
                """.formatted(email, captcha.id(), captcha.answer());
    }

    private static String resetRequest(String email, String code, String newPassword) {
        return """
                {"email":"%s","code":"%s","newPassword":"%s"}
                """.formatted(email, code, newPassword);
    }

    private static String loginRequest(String identifier, String password) {
        return """
                {"identifier":"%s","password":"%s"}
                """.formatted(identifier, password);
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

    private record Captcha(String id, String answer) {
    }
}
