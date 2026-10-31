package neko.fantasy.auth;

import com.jayway.jsonpath.JsonPath;
import neko.fantasy.auth.oauth.OAuthClient;
import neko.fantasy.auth.oauth.OAuthClientRepository;
import neko.fantasy.auth.oauth.OAuthException;
import neko.fantasy.auth.oauth.OAuthScopes;
import neko.fantasy.auth.repository.EmailVerificationSendLimitRepository;
import neko.fantasy.auth.settings.MailSecurity;
import neko.fantasy.auth.settings.MailService;
import neko.fantasy.auth.settings.SettingsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 服务端账号桥接：不依赖写死的 client_id，只认登记了 account:bridge scope 的机密客户端。
 */
@SpringBootTest(properties = "auth.registration.require-email-verification=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalAccountBridgeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OAuthClientRepository clientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailVerificationSendLimitRepository sendLimitRepository;

    @MockitoBean
    private SettingsService settingsService;

    @MockitoBean
    private MailService mailService;

    @Test
    void accountBridgeScopeCannotBeRequestedByBrowserAuthorization() {
        assertThatThrownBy(() -> OAuthScopes.parseRequested(OAuthScopes.ACCOUNT_BRIDGE))
                .isInstanceOf(OAuthException.class);
    }

    @Test
    void bridgeRequiresConfidentialClientWithDedicatedScope() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/internal/accounts/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest(email, "Password123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CLIENT"));

        Client noScope = createClient(false, List.of(OAuthScopes.OPENID));
        mockMvc.perform(post("/api/internal/accounts/login")
                        .header(HttpHeaders.AUTHORIZATION, basic(noScope))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest(email, "Password123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CLIENT"));

        Client publicClient = createClient(true, List.of(OAuthScopes.ACCOUNT_BRIDGE));
        mockMvc.perform(post("/api/internal/accounts/login")
                        .header(HttpHeaders.AUTHORIZATION, basic(publicClient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest(email, "Password123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CLIENT"));
    }

    @Test
    void registrationPasswordChangeAndResetStayAuthoritativeInAuth() throws Exception {
        when(settingsService.getSmtpConfig()).thenReturn(smtpConfig());
        Client client = createClient(false, List.of(OAuthScopes.ACCOUNT_BRIDGE));
        String email = uniqueEmail();

        mockMvc.perform(post("/api/internal/accounts/register/code")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<String> registrationCode = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendRegistrationCode(eq(email), eq(smtpConfig()),
                registrationCode.capture(), eq(Duration.ofMinutes(10)));

        MvcResult registration = mockMvc.perform(post("/api/internal/accounts/register")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password123","verificationCode":"%s"}
                                """.formatted(email, registrationCode.getValue())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();
        long authUserId = ((Number) JsonPath.read(
                registration.getResponse().getContentAsString(), "$.id")).longValue();
        assertThat(authUserId).isGreaterThanOrEqualTo(1_000_000L);

        mockMvc.perform(post("/api/internal/accounts/login")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest(email, "Password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(authUserId));

        mockMvc.perform(post("/api/internal/accounts/password/change")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","currentPassword":"Password123","newPassword":"Password456"}
                                """.formatted(email)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/internal/accounts/login")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest(email, "Password123")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/internal/accounts/login")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest(email, "Password456")))
                .andExpect(status().isOk());

        var sendLimit = sendLimitRepository.findById(email).orElseThrow();
        sendLimit.setLastSentAt(Instant.now().minusSeconds(61));
        sendLimitRepository.saveAndFlush(sendLimit);

        mockMvc.perform(post("/api/internal/accounts/password/reset-code")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<String> resetCode = ArgumentCaptor.forClass(String.class);
        verify(mailService, timeout(2_000)).sendPasswordResetCode(
                eq(email), eq(smtpConfig()), resetCode.capture(), eq(Duration.ofMinutes(10)));

        mockMvc.perform(post("/api/internal/accounts/password/reset")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","code":"%s","newPassword":"Password789"}
                                """.formatted(email, resetCode.getValue())))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/internal/accounts/login")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest(email, "Password789")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/internal/accounts/password/admin-reset")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"newPassword\":\"AdminSet123\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/internal/accounts/login")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest(email, "AdminSet123")))
                .andExpect(status().isOk());
    }

    @Test
    void wrongSecretIsRejected() throws Exception {
        Client client = createClient(false, List.of(OAuthScopes.ACCOUNT_BRIDGE));
        mockMvc.perform(post("/api/internal/accounts/login")
                        .header(HttpHeaders.AUTHORIZATION, basic(client.clientId(), "wrong-secret"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest(uniqueEmail(), "Password123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CLIENT"));
    }

    @Test
    void issuedTokensCanBeIntrospectedExchangedAndRevoked() throws Exception {
        when(settingsService.getSmtpConfig()).thenReturn(smtpConfig());
        Client client = createClient(false, List.of(OAuthScopes.ACCOUNT_BRIDGE, OAuthScopes.EMAIL));
        String email = uniqueEmail();

        mockMvc.perform(post("/api/internal/accounts/register/code")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<String> registrationCode = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendRegistrationCode(eq(email), eq(smtpConfig()),
                registrationCode.capture(), eq(Duration.ofMinutes(10)));

        MvcResult registration = mockMvc.perform(post("/api/internal/accounts/register")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password123","verificationCode":"%s"}
                                """.formatted(email, registrationCode.getValue())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andReturn();
        String accessToken = JsonPath.read(
                registration.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(post("/oauth2/introspect")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.token_type").value("Bearer"));

        MvcResult exchanged = mockMvc.perform(post("/api/internal/accounts/token/exchange")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"" + accessToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andReturn();
        String longLivedToken = JsonPath.read(
                exchanged.getResponse().getContentAsString(), "$.accessToken");

        Client otherClient = createClient(false, List.of(OAuthScopes.ACCOUNT_BRIDGE));
        mockMvc.perform(post("/api/internal/accounts/token/exchange")
                        .header(HttpHeaders.AUTHORIZATION, basic(otherClient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"" + accessToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        mockMvc.perform(post("/oauth2/revoke")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", accessToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/oauth2/introspect")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/api/internal/accounts/token/exchange")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"" + accessToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        mockMvc.perform(post("/api/internal/accounts/password/change")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","currentPassword":"Password123","newPassword":"Password456"}
                                """.formatted(email)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/oauth2/introspect")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", longLivedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    private Client createClient(boolean publicClient, List<String> scopes) {
        String clientId = "bridge_" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        String secret = publicClient ? null : "secret-" + UUID.randomUUID();
        String secretHash = secret == null ? null : passwordEncoder.encode(secret);
        clientRepository.saveAndFlush(new OAuthClient(
                clientId, secretHash, "账号桥接测试",
                "服务端账号桥接集成测试", List.of("https://music.example/oauth/callback"),
                scopes, publicClient));
        return new Client(clientId, secret);
    }

    private static String loginRequest(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    private static String basic(Client client) {
        return basic(client.clientId(), client.clientSecret());
    }

    private static String basic(String clientId, String secret) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
    }

    private static String uniqueEmail() {
        return "bridge_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@gmail.com";
    }

    private static SettingsService.SmtpConfig smtpConfig() {
        return new SettingsService.SmtpConfig("NekoAuth", "smtp.example.com", 587,
                "mailer", "password", MailSecurity.STARTTLS, true, "noreply@example.com",
                "NekoAuth", 10_000, 10_000, 10_000);
    }

    private record Client(String clientId, String clientSecret) {
    }
}
