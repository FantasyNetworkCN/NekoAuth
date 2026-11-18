package neko.fantasy.auth;

import com.jayway.jsonpath.JsonPath;
import neko.fantasy.auth.settings.ApplicationSettings;
import neko.fantasy.auth.settings.ApplicationSettingsRepository;
import neko.fantasy.auth.settings.SettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
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

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void publicPolicyExposesDefaultEmailDomainWhitelist() throws Exception {
        mockMvc.perform(get("/api/auth/registration-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.emailVerificationRequired").value(false))
                .andExpect(jsonPath("$.smsRegistrationEnabled").value(false))
                .andExpect(jsonPath("$.emailDomains", contains(
                        "qq.com", "gmail.com", "163.com", "139.com",
                        "126.com", "outlook.com", "hotmail.com", "icloud.com")))
                .andExpect(jsonPath("$.emailCodeExpiresInSeconds").value(600))
                .andExpect(jsonPath("$.emailCodeResendAfterSeconds").value(60))
                .andExpect(jsonPath("$.emailCodeDailyLimit").value(5))
                .andExpect(jsonPath("$.emailCodeWindowSeconds").value(86400));
    }

    @Test
    void phoneRegistrationEndpointsAreDisabledByDefault() throws Exception {
        mockMvc.perform(post("/api/auth/register/phone/code").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900000104","captchaId":"test","captchaAnswer":"ABCDE"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SMS_REGISTRATION_DISABLED"));
        mockMvc.perform(post("/api/auth/register/phone").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900000104","password":"Password123","verificationCode":"123456"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SMS_REGISTRATION_DISABLED"));
    }

    @Test
    void adminCanConfigureSmsWithoutEchoingTheEncryptedSecret() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/admin/settings").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sms.enabled").value(false))
                .andExpect(jsonPath("$.sms.accessKeyId").value(""))
                .andExpect(jsonPath("$.sms.accessKeySecretConfigured").value(false))
                .andExpect(jsonPath("$.sms.signName").value(""))
                .andExpect(jsonPath("$.sms.templateCode").value("100001"))
                .andExpect(jsonPath("$.sms.schemeName").value(""))
                .andExpect(jsonPath("$.sms.codeParameter").value(""))
                .andExpect(jsonPath("$.sms.minutesParameter").value(""));

        updateSms(token, true, """
                {"enabled":true,"accessKeyId":"test-id","signName":"猫屋"}
                """).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SMS_SETTINGS_REQUIRED"));

        var update = """
                {"enabled":true,"accessKeyId":"test-id","accessKeySecret":"test-secret",
                 "signName":"猫屋","templateCode":"100123","schemeName":"猫屋注册","minutesParameter":"min"}
                """;
        updateSms(token, true, update.replace("\"templateCode\":\"100123\",", "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.sms.enabled").value(true))
                .andExpect(jsonPath("$.sms.templateCode").value("100001"))
                .andExpect(jsonPath("$.sms.accessKeySecretConfigured").value(true))
                .andExpect(jsonPath("$.sms.accessKeySecret").doesNotExist());

        String ciphertext = jdbc.queryForObject("select sms_access_key_secret_ciphertext from application_settings where id = 1", String.class);
        assertThat(ciphertext).startsWith("v1:").doesNotContain("test-secret");
        assertThat(settingsService.getSmsConfig().accessKeySecret()).isEqualTo("test-secret");
        assertThat(settingsService.getSmsConfig().codeParameter()).isEqualTo("code");

        mockMvc.perform(get("/api/auth/registration-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.smsRegistrationEnabled").value(true))
                .andExpect(jsonPath("$.sms").doesNotExist());

        updateSms(token, false, update.replace("\"accessKeySecret\":\"test-secret\",", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sms.templateCode").value("100001"))
                .andExpect(jsonPath("$.sms.accessKeySecretConfigured").value(true));
        assertThat(jdbc.queryForObject("select sms_access_key_secret_ciphertext from application_settings where id = 1", String.class))
                .isEqualTo(ciphertext);
        mockMvc.perform(post("/api/auth/register/phone/code").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900000104","captchaId":"test","captchaAnswer":"ABCDE"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REGISTRATION_DISABLED"));

        updateSms(token, true, """
                {"enabled":false,"clearAccessKeySecret":true}
                """).andExpect(status().isOk())
                .andExpect(jsonPath("$.sms.enabled").value(false))
                .andExpect(jsonPath("$.sms.accessKeySecretConfigured").value(false));
        mockMvc.perform(get("/api/auth/registration-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.smsRegistrationEnabled").value(false));
    }

    @Test
    void registrationRejectsDomainOutsideWhitelist() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"outside@example.com","password":"Password123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_DOMAIN_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value("不允许的邮箱"));
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
        updateSms(token, true, "{}")
                .andExpect(status().isForbidden());
    }

    private ResultActions updateSms(String token, boolean registrationEnabled, String sms) throws Exception {
        return mockMvc.perform(put("/api/admin/settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"serviceName":"NekoAuth","registrationEnabled":%s,"emailDomainWhitelist":["gmail.com"],
                         "smtp":{"enabled":false,"port":587,"security":"STARTTLS","authEnabled":true,"clearPassword":false,
                                 "connectionTimeoutMs":10000,"readTimeoutMs":10000,"writeTimeoutMs":10000},
                         "sms":%s}
                        """.formatted(registrationEnabled, sms)));
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
