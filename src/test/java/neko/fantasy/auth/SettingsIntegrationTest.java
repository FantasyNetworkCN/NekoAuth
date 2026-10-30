package neko.fantasy.auth;

import com.jayway.jsonpath.JsonPath;
import neko.fantasy.auth.settings.ApplicationSettings;
import neko.fantasy.auth.settings.ApplicationSettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SettingsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationSettingsRepository settingsRepository;

    @Test
    void publicPolicyExposesDefaultEmailDomainWhitelist() throws Exception {
        mockMvc.perform(get("/api/auth/registration-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.emailVerificationRequired").value(false))
                .andExpect(jsonPath("$.emailDomains", contains(
                        "qq.com", "gmail.com", "163.com", "139.com",
                        "126.com", "outlook.com", "hotmail.com", "icloud.com")))
                .andExpect(jsonPath("$.emailCodeExpiresInSeconds").value(600))
                .andExpect(jsonPath("$.emailCodeResendAfterSeconds").value(60))
                .andExpect(jsonPath("$.emailCodeDailyLimit").value(5))
                .andExpect(jsonPath("$.emailCodeWindowSeconds").value(86400));
    }

    @Test
    void registrationRejectsDomainOutsideWhitelist() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"outside@example.com","password":"Password123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_DOMAIN_NOT_ALLOWED"));
    }

    @Test
    void adminCanUpdateWhitelistAndSmtpSettingsWithoutPasswordEcho() throws Exception {
        String adminToken = adminToken();
        String payload = """
                {
                  "serviceName": "NekoAuth Test",
                  "registrationEnabled": true,
                  "emailDomainWhitelist": ["nekocore.cn", "gmail.com"],
                  "smtp": {
                    "enabled": false,
                    "host": "smtp.example.com",
                    "port": 587,
                    "username": "mailer@example.com",
                    "password": "app-password",
                    "clearPassword": false,
                    "security": "STARTTLS",
                    "authEnabled": true,
                    "fromEmail": "noreply@nekocore.cn",
                    "fromName": "NekoAuth Test",
                    "connectionTimeoutMs": 10000,
                    "readTimeoutMs": 10000,
                    "writeTimeoutMs": 10000
                  }
                }
                """;

        mockMvc.perform(put("/api/admin/settings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceName").value("NekoAuth Test"))
                .andExpect(jsonPath("$.emailDomainWhitelist", contains("nekocore.cn", "gmail.com")))
                .andExpect(jsonPath("$.smtp.passwordConfigured").value(true))
                .andExpect(jsonPath("$.smtp.password").doesNotExist());

        ApplicationSettings stored = settingsRepository.findById(ApplicationSettings.SINGLETON_ID).orElseThrow();
        assertThat(stored.getSmtpPasswordCiphertext())
                .startsWith("v1:")
                .doesNotContain("app-password");

        mockMvc.perform(get("/api/auth/registration-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailDomains", contains("nekocore.cn", "gmail.com")));

        String username = "settings_" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@nekocore.cn","password":"Password123"}
                                """.formatted(username, username)))
                .andExpect(status().isCreated());
    }

    @Test
    void adminCanCloseRegistrationAndMailTestFailsClearlyWhenDisabled() throws Exception {
        String adminToken = adminToken();
        String payload = """
                {
                  "serviceName": "NekoAuth",
                  "registrationEnabled": false,
                  "emailDomainWhitelist": ["gmail.com"],
                  "smtp": {
                    "enabled": false,
                    "host": "",
                    "port": 587,
                    "username": "",
                    "password": "",
                    "clearPassword": false,
                    "security": "STARTTLS",
                    "authEnabled": true,
                    "fromEmail": "",
                    "fromName": "NekoAuth",
                    "connectionTimeoutMs": 10000,
                    "readTimeoutMs": 10000,
                    "writeTimeoutMs": 10000
                  }
                }
                """;

        mockMvc.perform(put("/api/admin/settings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"closed@gmail.com","password":"Password123"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REGISTRATION_DISABLED"));

        mockMvc.perform(post("/api/admin/settings/mail/test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient\":\"admin@gmail.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SMTP_DISABLED"));
    }

    @Test
    void nonAdminCannotReadOrChangeSettings() throws Exception {
        String username = "member_" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@gmail.com","password":"Password123"}
                                """.formatted(username, username)))
                .andExpect(status().isCreated())
                .andReturn();
        String token = JsonPath.read(registration.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(get("/api/admin/settings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private String adminToken() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"admin","password":"AdminPass123"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
    }
}
