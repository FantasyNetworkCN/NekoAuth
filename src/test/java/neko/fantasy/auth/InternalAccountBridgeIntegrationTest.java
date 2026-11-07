package neko.fantasy.auth;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import neko.fantasy.auth.oauth.OAuthClient;
import neko.fantasy.auth.oauth.OAuthClientRepository;
import neko.fantasy.auth.oauth.OAuthException;
import neko.fantasy.auth.oauth.OAuthScopes;
import neko.fantasy.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
    private UserRepository userRepository;

    @Test
    void accountBridgeScopeCannotBeRequestedByBrowserAuthorization() {
        assertThatThrownBy(() -> OAuthScopes.parseRequested(OAuthScopes.ACCOUNT_BRIDGE))
                .isInstanceOf(OAuthException.class);
    }

    @Test
    void bridgeRequiresConfidentialClientWithDedicatedScope() throws Exception {
        String body = "{\"authUserIds\":[1000000]}";
        mockMvc.perform(post("/api/internal/accounts/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CLIENT"));

        Client noScope = createClient(false, List.of(OAuthScopes.OPENID));
        mockMvc.perform(post("/api/internal/accounts/lookup")
                        .header(HttpHeaders.AUTHORIZATION, basic(noScope))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CLIENT"));

        Client publicClient = createClient(true, List.of(OAuthScopes.ACCOUNT_BRIDGE));
        mockMvc.perform(post("/api/internal/accounts/lookup")
                        .header(HttpHeaders.AUTHORIZATION, basic(publicClient))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CLIENT"));
    }

    @Test
    void adminCreateStoresAndReturnsNicknameWithoutIssuingAccessToken() throws Exception {
        Client client = createClient(false, List.of(OAuthScopes.ACCOUNT_BRIDGE));
        String email = uniqueEmail();

        mockMvc.perform(post("/api/internal/accounts/admin-create")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"后台建号猫娘","email":"%s","password":"Password123"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.nickname").value("后台建号猫娘"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        assertThat(userRepository.findByEmail(email).orElseThrow().getDisplayName())
                .isEqualTo("后台建号猫娘");
    }

    @Test
    void bridgeCanLookupNicknamesAndSearchAccounts() throws Exception {
        Client client = createClient(false, List.of(OAuthScopes.ACCOUNT_BRIDGE));
        String email = uniqueEmail();
        String nickname = "查询猫娘_" + UUID.randomUUID().toString().substring(0, 8);

        MvcResult created = mockMvc.perform(post("/api/internal/accounts/admin-create")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"%s","email":"%s","password":"Password123"}
                                """.formatted(nickname, email)))
                .andExpect(status().isCreated())
                .andReturn();
        long authUserId = ((Number) JsonPath.read(
                created.getResponse().getContentAsString(), "$.id")).longValue();

        mockMvc.perform(post("/api/internal/accounts/lookup")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authUserIds\":[" + authUserId + ",999999999]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(authUserId))
                .andExpect(jsonPath("$[0].nickname").value(nickname));

        mockMvc.perform(post("/api/internal/accounts/search")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"" + nickname + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(authUserId))
                .andExpect(jsonPath("$[0].nickname").value(nickname));
    }

    @Test
    void bridgeCanMigrateAvatarAndAvatarIsPubliclyServed() throws Exception {
        Client client = createClient(false, List.of(OAuthScopes.ACCOUNT_BRIDGE));
        String email = uniqueEmail();
        MvcResult created = mockMvc.perform(post("/api/internal/accounts/admin-create")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"头像猫娘","email":"%s","password":"Password123"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        Number userId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        byte[] png = png1x1();
        MockMultipartFile avatar = new MockMultipartFile(
                "avatar", "avatar.png", MediaType.IMAGE_PNG_VALUE, png);

        mockMvc.perform(multipart("/api/internal/accounts/avatar")
                        .file(avatar)
                        .param("authUserId", String.valueOf(userId))
                        .header(HttpHeaders.AUTHORIZATION, basic(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value("/avatars/" + userId));

        MvcResult image = mockMvc.perform(get("/avatars/" + userId))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(image.getResponse().getContentType()).startsWith(MediaType.IMAGE_PNG_VALUE);
        assertThat(image.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(image.getResponse().getContentAsByteArray()).isEqualTo(png);
    }

    @Test
    void missingAvatarFallsBackToAuthFavicon() throws Exception {
        MvcResult image = mockMvc.perform(get("/avatars/999999"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(image.getResponse().getContentType()).startsWith("image/x-icon");
        assertThat(image.getResponse().getContentAsByteArray()).isNotEmpty();
    }

    @Test
    void avatarUploadRejectsFakeOversizedAndNonImageFiles() throws Exception {
        Client client = createClient(false, List.of(OAuthScopes.ACCOUNT_BRIDGE));
        String email = uniqueEmail();
        MvcResult created = mockMvc.perform(post("/api/internal/accounts/admin-create")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"头像校验","email":"%s","password":"Password123"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        Number userId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        byte[] validPng = png1x1();
        byte[] fakePng = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        byte[] oversized = Arrays.copyOf(validPng, 10 * 1024 * 1024 + 1);

        assertBadAvatar(client, userId.longValue(),
                new MockMultipartFile("avatar", "fake.png", MediaType.IMAGE_PNG_VALUE, fakePng));
        assertBadAvatar(client, userId.longValue(),
                new MockMultipartFile("avatar", "large.png", MediaType.IMAGE_PNG_VALUE, oversized));
        assertBadAvatar(client, userId.longValue(),
                new MockMultipartFile("avatar", "image.png", MediaType.TEXT_HTML_VALUE, validPng));
    }

    @Test
    void wrongSecretIsRejected() throws Exception {
        Client client = createClient(false, List.of(OAuthScopes.ACCOUNT_BRIDGE));
        mockMvc.perform(post("/api/internal/accounts/lookup")
                        .header(HttpHeaders.AUTHORIZATION, basic(client.clientId(), "wrong-secret"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authUserIds\":[1000000]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CLIENT"));
    }

    @Test
    void bridgeExchangesParentDomainSsoSessionAndRevokesItsFamily() throws Exception {
        Client client = createClient(false, List.of(OAuthScopes.ACCOUNT_BRIDGE));
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"admin","password":"AdminPass123"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        Cookie refresh = login.getResponse().getCookie("refresh_token");
        Cookie sso = login.getResponse().getCookie("nekoauth_sso_session");
        assertThat(refresh).isNotNull();
        assertThat(sso).isNotNull();
        assertThat(sso.getDomain()).isEqualTo("fantasy.example");
        assertThat(sso.isHttpOnly()).isTrue();

        MvcResult issued = mockMvc.perform(post("/api/internal/accounts/sso-session")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ssoSession\":\"" + sso.getValue() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1000000))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        String accessToken = JsonPath.read(
                issued.getResponse().getContentAsString(), "$.accessToken");

        MvcResult logout = mockMvc.perform(post("/api/internal/accounts/sso-session/logout")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ssoSession\":\"" + sso.getValue() + "\"}"))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(logout.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(value -> value.startsWith("nekoauth_sso_session="));

        mockMvc.perform(post("/oauth2/introspect")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/api/auth/refresh").cookie(refresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void issuedTokensCanBeIntrospectedExchangedAndRevoked() throws Exception {
        Client client = createClient(false, List.of(OAuthScopes.ACCOUNT_BRIDGE, OAuthScopes.EMAIL));
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"admin","password":"AdminPass123"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        Cookie sso = login.getResponse().getCookie("nekoauth_sso_session");
        assertThat(sso).isNotNull();

        MvcResult issued = mockMvc.perform(post("/api/internal/accounts/sso-session")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ssoSession\":\"" + sso.getValue() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andReturn();
        String accessToken = JsonPath.read(
                issued.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(post("/oauth2/introspect")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
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
        mockMvc.perform(post("/oauth2/introspect")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", longLivedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

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
    }

    @Test
    void adminResetPasswordRemainsAvailable() throws Exception {
        Client client = createClient(false, List.of(OAuthScopes.ACCOUNT_BRIDGE));
        String email = uniqueEmail();
        mockMvc.perform(post("/api/internal/accounts/admin-create")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"重置测试","email":"%s","password":"Password123"}
                                """.formatted(email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/internal/accounts/password/admin-reset")
                        .header(HttpHeaders.AUTHORIZATION, basic(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"newPassword\":\"AdminSet123\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"Password123"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"AdminSet123"}
                                """.formatted(email)))
                .andExpect(status().isOk());
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

    private static byte[] png1x1() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private void assertBadAvatar(Client client, long userId, MockMultipartFile avatar) throws Exception {
        mockMvc.perform(multipart("/api/internal/accounts/avatar")
                        .file(avatar)
                        .param("authUserId", String.valueOf(userId))
                        .header(HttpHeaders.AUTHORIZATION, basic(client)))
                .andExpect(status().isBadRequest());
    }

    private record Client(String clientId, String clientSecret) {
    }
}
