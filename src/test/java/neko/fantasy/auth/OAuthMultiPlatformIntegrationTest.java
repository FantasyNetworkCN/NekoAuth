package neko.fantasy.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import neko.fantasy.auth.oauth.OAuthKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OAuthMultiPlatformIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String WEB_REDIRECT = "https://web.fantasy.example/oauth/callback";
    private static final String DESKTOP_REDIRECT = "https://desktop.fantasy.example/oauth/callback";
    private static final String WEB_LOGOUT = "https://web.fantasy.example/logged-out";
    private static final String DESKTOP_LOGOUT = "https://desktop.fantasy.example/logged-out";
    private static final String NATIVE_REDIRECT = "com.nekoauth.test:/oauth/callback";
    private static final String NATIVE_LOGOUT = "com.nekoauth.test:/logged-out";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OAuthKeyService oauthKeyService;

    @Test
    void twoPlatformsKeepIndependentRefreshSessions() throws Exception {
        String adminToken = login("admin", "AdminPass123");
        Client web = createClient(adminToken, true, List.of(WEB_REDIRECT), List.of(WEB_LOGOUT),
                List.of("openid", "offline_access"));
        Client desktop = createClient(adminToken, true, List.of(DESKTOP_REDIRECT), List.of(DESKTOP_LOGOUT),
                List.of("openid", "offline_access"));
        String username = uniqueUsername("multi");
        String userToken = register(username);

        Tokens webTokens = authorizeAndExchange(userToken, web, WEB_REDIRECT, username);
        Tokens desktopTokens = authorizeAndExchange(userToken, desktop, DESKTOP_REDIRECT, username);

        assertThat(webTokens.refreshToken()).isNotEqualTo(desktopTokens.refreshToken());
        assertIntrospection(web.clientId(), webTokens.refreshToken(), true);
        assertIntrospection(desktop.clientId(), desktopTokens.refreshToken(), true);

        mockMvc.perform(post("/oauth2/revoke")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", web.clientId())
                        .param("token", webTokens.refreshToken()))
                .andExpect(status().isOk());

        assertIntrospection(web.clientId(), webTokens.refreshToken(), false);
        assertIntrospection(desktop.clientId(), desktopTokens.refreshToken(), true);
    }

    @Test
    void rpInitiatedLogoutOnlyRevokesTheRequestingPlatform() throws Exception {
        String adminToken = login("admin", "AdminPass123");
        Client web = createClient(adminToken, true, List.of(WEB_REDIRECT), List.of(WEB_LOGOUT),
                List.of("openid", "offline_access"));
        Client desktop = createClient(adminToken, true, List.of(DESKTOP_REDIRECT), List.of(DESKTOP_LOGOUT),
                List.of("openid", "offline_access"));
        String username = uniqueUsername("logout");
        String userToken = register(username);

        Tokens webTokens = authorizeAndExchange(userToken, web, WEB_REDIRECT, username);
        Tokens desktopTokens = authorizeAndExchange(userToken, desktop, DESKTOP_REDIRECT, username);
        String state = "logout-state-" + UUID.randomUUID();

        MvcResult logout = mockMvc.perform(get("/oauth2/logout")
                        .param("id_token_hint", webTokens.idToken())
                        .param("post_logout_redirect_uri", WEB_LOGOUT)
                        .param("state", state))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("refresh_token=")))
                .andReturn();

        String location = logout.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).startsWith(WEB_LOGOUT);
        assertThat(queryParameters(location).get("state")).isEqualTo(state);
        assertIntrospection(web.clientId(), webTokens.refreshToken(), false);
        assertIntrospection(desktop.clientId(), desktopTokens.refreshToken(), true);

        mockMvc.perform(get("/oauth2/logout")
                        .param("id_token_hint", desktopTokens.idToken())
                        .param("post_logout_redirect_uri", "https://evil.example/logged-out"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void expiredIdTokenHintCanStillCompleteRpInitiatedLogout() throws Exception {
        String adminToken = login("admin", "AdminPass123");
        Client web = createClient(adminToken, true, List.of(WEB_REDIRECT), List.of(WEB_LOGOUT),
                List.of("openid", "offline_access"));
        Client desktop = createClient(adminToken, true, List.of(DESKTOP_REDIRECT), List.of(DESKTOP_LOGOUT),
                List.of("openid", "offline_access"));
        String username = uniqueUsername("expired_hint");
        String userToken = register(username);
        Tokens webTokens = authorizeAndExchange(userToken, web, WEB_REDIRECT, username);
        Tokens desktopTokens = authorizeAndExchange(userToken, desktop, DESKTOP_REDIRECT, username);
        Map<String, Object> claims = jwtClaims(webTokens.idToken());

        String expiredHint = oauthKeyService.issue(
                Map.of(
                        "aud", web.clientId(),
                        "azp", web.clientId(),
                        "auth_time", claims.get("auth_time")),
                claims.get("sub").toString(),
                Duration.ofMinutes(-5));

        mockMvc.perform(get("/oauth2/logout")
                        .param("id_token_hint", expiredHint)
                        .param("post_logout_redirect_uri", WEB_LOGOUT))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.startsWith(WEB_LOGOUT)));

        assertIntrospection(web.clientId(), webTokens.refreshToken(), false);
        assertIntrospection(desktop.clientId(), desktopTokens.refreshToken(), true);
    }

    @Test
    void clientIdOnlyLogoutUsesRegisteredRedirectAndRejectsUnknownTargets() throws Exception {
        String adminToken = login("admin", "AdminPass123");
        Client client = createClient(adminToken, true, List.of(WEB_REDIRECT), List.of(WEB_LOGOUT),
                List.of("openid"));
        String state = "client-only-" + UUID.randomUUID();

        MvcResult result = mockMvc.perform(get("/oauth2/logout")
                        .param("client_id", client.clientId())
                        .param("post_logout_redirect_uri", WEB_LOGOUT)
                        .param("state", state))
                .andExpect(status().isFound())
                .andReturn();
        String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).startsWith(WEB_LOGOUT);
        assertThat(queryParameters(location)).containsEntry("state", state);

        mockMvc.perform(get("/oauth2/logout")
                        .param("client_id", client.clientId())
                        .param("post_logout_redirect_uri", "https://evil.example/logged-out"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void adminCanUpdateRegisteredPostLogoutRedirectUris() throws Exception {
        String adminToken = login("admin", "AdminPass123");
        Client client = createClient(adminToken, true, List.of(WEB_REDIRECT), List.of(WEB_LOGOUT),
                List.of("openid"));
        String updatedLogout = "https://web.fantasy.example/session-ended";

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/admin/oauth-clients/{id}", client.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of(
                                "postLogoutRedirectUris", List.of(updatedLogout)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postLogoutRedirectUris[0]").value(updatedLogout));

        mockMvc.perform(get("/oauth2/logout")
                        .param("client_id", client.clientId())
                        .param("post_logout_redirect_uri", updatedLogout))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.startsWith(updatedLogout)));

        mockMvc.perform(get("/oauth2/logout")
                        .param("client_id", client.clientId())
                        .param("post_logout_redirect_uri", WEB_LOGOUT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void authorizationSupportsLoginHintPromptLoginMaxAgeAndPromptNone() throws Exception {
        String adminToken = login("admin", "AdminPass123");
        Client client = createClient(adminToken, true, List.of(WEB_REDIRECT), List.of(WEB_LOGOUT),
                List.of("openid"));
        String username = uniqueUsername("prompt");
        String userToken = register(username);
        String verifier = randomUrlToken(48);
        String challenge = base64UrlSha256(verifier);

        MvcResult none = mockMvc.perform(get("/api/oauth/authorize")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("client_id", client.clientId())
                        .param("redirect_uri", WEB_REDIRECT)
                        .param("response_type", "code")
                        .param("scope", "openid")
                        .param("state", "none-state")
                        .param("prompt", "none")
                        .param("code_challenge", challenge)
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isOk())
                .andReturn();
        String noneRedirect = JsonPath.read(none.getResponse().getContentAsString(), "$.redirectUrl");
        assertThat(queryParameters(noneRedirect)).containsEntry("error", "consent_required");

        mockMvc.perform(get("/api/oauth/authorize")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("client_id", client.clientId())
                        .param("redirect_uri", WEB_REDIRECT)
                        .param("response_type", "code")
                        .param("scope", "openid")
                        .param("prompt", "login")
                        .param("login_hint", username)
                        .param("code_challenge", challenge)
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reauthRequired").value(true))
                .andExpect(jsonPath("$.loginHint").value(username))
                .andExpect(jsonPath("$.redirectUrl").doesNotExist());

        mockMvc.perform(get("/api/oauth/authorize")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("client_id", client.clientId())
                        .param("redirect_uri", WEB_REDIRECT)
                        .param("response_type", "code")
                        .param("scope", "openid")
                        .param("prompt", "select_account")
                        .param("code_challenge", challenge)
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reauthRequired").value(true));

        mockMvc.perform(get("/api/oauth/authorize")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("client_id", client.clientId())
                        .param("redirect_uri", WEB_REDIRECT)
                        .param("response_type", "code")
                        .param("scope", "openid")
                        .param("max_age", "0")
                        .param("code_challenge", challenge)
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reauthRequired").value(true));

        mockMvc.perform(get("/api/oauth/authorize")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("client_id", client.clientId())
                        .param("redirect_uri", WEB_REDIRECT)
                        .param("response_type", "code")
                        .param("scope", "openid")
                        .param("max_age", "3600")
                        .param("login_hint", username)
                        .param("code_challenge", challenge)
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reauthRequired").value(false))
                .andExpect(jsonPath("$.loginHint").value(username));

        mockMvc.perform(get("/api/oauth/authorize")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("client_id", client.clientId())
                        .param("redirect_uri", WEB_REDIRECT)
                        .param("response_type", "code")
                        .param("scope", "openid")
                        .param("prompt", "none login"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));

        mockMvc.perform(get("/api/oauth/authorize")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("client_id", client.clientId())
                        .param("redirect_uri", WEB_REDIRECT)
                        .param("response_type", "code")
                        .param("scope", "openid")
                        .param("max_age", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void anonymousPromptNoneReturnsLoginRequiredWithoutShowingLoginUi() throws Exception {
        String adminToken = login("admin", "AdminPass123");
        Client client = createClient(adminToken, true, List.of(WEB_REDIRECT), List.of(WEB_LOGOUT),
                List.of("openid"));
        String verifier = randomUrlToken(48);

        MvcResult result = mockMvc.perform(get("/api/oauth/authorize")
                        .param("client_id", client.clientId())
                        .param("redirect_uri", WEB_REDIRECT)
                        .param("response_type", "code")
                        .param("scope", "openid")
                        .param("state", "anonymous-none")
                        .param("prompt", "none")
                        .param("code_challenge", base64UrlSha256(verifier))
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isOk())
                .andReturn();
        String redirectUrl = JsonPath.read(result.getResponse().getContentAsString(), "$.redirectUrl");
        assertThat(queryParameters(redirectUrl)).containsEntry("error", "login_required");
        assertThat(queryParameters(redirectUrl)).containsEntry("state", "anonymous-none");
    }

    @Test
    void nativePublicClientCanUsePrivateSchemeButConfidentialClientCannot() throws Exception {
        String adminToken = login("admin", "AdminPass123");

        mockMvc.perform(post("/api/admin/oauth-clients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson("非法机密客户端", false, List.of(NATIVE_REDIRECT),
                                List.of(NATIVE_LOGOUT), List.of("openid", "offline_access"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REDIRECT_URI"));

        mockMvc.perform(post("/api/admin/oauth-clients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson("危险协议回调", true, List.of("javascript:alert(1)"),
                                List.of(), List.of("openid"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REDIRECT_URI"));

        mockMvc.perform(post("/api/admin/oauth-clients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson("危险登出回调", true, List.of(WEB_REDIRECT),
                                List.of("data:text/html,logged-out"), List.of("openid"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_POST_LOGOUT_REDIRECT_URI"));

        Client nativeClient = createClient(adminToken, true, List.of(NATIVE_REDIRECT),
                List.of(NATIVE_LOGOUT), List.of("openid", "offline_access"));
        String username = uniqueUsername("native");
        String userToken = register(username);
        String verifier = randomUrlToken(48);
        String challenge = base64UrlSha256(verifier);

        mockMvc.perform(get("/api/oauth/authorize")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("client_id", nativeClient.clientId())
                        .param("redirect_uri", NATIVE_REDIRECT)
                        .param("response_type", "code")
                        .param("scope", "openid offline_access")
                        .param("state", "native-state")
                        .param("code_challenge", challenge)
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectHost").value("com.nekoauth.test"));

        MvcResult decision = mockMvc.perform(post("/api/oauth/authorize/decision")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientId":"%s",
                                  "redirectUri":"%s",
                                  "responseType":"code",
                                  "scope":"openid offline_access",
                                  "state":"native-state",
                                  "codeChallenge":"%s",
                                  "codeChallengeMethod":"S256",
                                  "approved":true
                                }
                                """.formatted(nativeClient.clientId(), NATIVE_REDIRECT, challenge)))
                .andExpect(status().isOk())
                .andReturn();
        String redirectUrl = JsonPath.read(decision.getResponse().getContentAsString(), "$.redirectUrl");
        assertThat(redirectUrl).startsWith(NATIVE_REDIRECT + "?");
        assertThat(queryParameters(redirectUrl)).containsEntry("state", "native-state");
        String code = queryParameters(redirectUrl).get("code");

        MvcResult tokenResult = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", nativeClient.clientId())
                        .param("code", code)
                        .param("redirect_uri", NATIVE_REDIRECT)
                        .param("code_verifier", verifier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andReturn();
        String idToken = JsonPath.read(tokenResult.getResponse().getContentAsString(), "$.id_token");

        MvcResult logout = mockMvc.perform(get("/oauth2/logout")
                        .param("id_token_hint", idToken)
                        .param("post_logout_redirect_uri", NATIVE_LOGOUT)
                        .param("state", "native-logout"))
                .andExpect(status().isFound())
                .andReturn();
        String location = logout.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).startsWith(NATIVE_LOGOUT + "?");
        assertThat(queryParameters(location)).containsEntry("state", "native-logout");
    }

    private Tokens authorizeAndExchange(String userToken, Client client, String redirectUri,
                                        String username) throws Exception {
        String verifier = randomUrlToken(48);
        String challenge = base64UrlSha256(verifier);
        mockMvc.perform(get("/api/oauth/authorize")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("client_id", client.clientId())
                        .param("redirect_uri", redirectUri)
                        .param("response_type", "code")
                        .param("scope", "openid offline_access")
                        .param("login_hint", username)
                        .param("code_challenge", challenge)
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isOk());

        MvcResult decision = mockMvc.perform(post("/api/oauth/authorize/decision")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientId":"%s",
                                  "redirectUri":"%s",
                                  "responseType":"code",
                                  "scope":"openid offline_access",
                                  "codeChallenge":"%s",
                                  "codeChallengeMethod":"S256",
                                  "approved":true
                                }
                                """.formatted(client.clientId(), redirectUri, challenge)))
                .andExpect(status().isOk())
                .andReturn();
        String code = queryParameters(JsonPath.read(
                decision.getResponse().getContentAsString(), "$.redirectUrl")).get("code");
        assertThat(code).isNotBlank();

        MvcResult tokenResult = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", client.clientId())
                        .param("code", code)
                        .param("redirect_uri", redirectUri)
                        .param("code_verifier", verifier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andReturn();
        Map<String, Object> body = JSON.readValue(
                tokenResult.getResponse().getContentAsString(), new TypeReference<>() { });
        String idToken = (String) body.get("id_token");
        Map<String, Object> claims = jwtClaims(idToken);
        assertThat(((Number) claims.get("auth_time")).longValue())
                .isLessThanOrEqualTo(java.time.Instant.now().getEpochSecond());
        return new Tokens(idToken, (String) body.get("refresh_token"));
    }

    private void assertIntrospection(String clientId, String refreshToken, boolean active) throws Exception {
        mockMvc.perform(post("/oauth2/introspect")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", clientId)
                        .param("token", refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(active));
    }

    private Client createClient(String adminToken, boolean publicClient, List<String> redirectUris,
                                List<String> postLogoutRedirectUris, List<String> scopes) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/oauth-clients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientJson("多平台测试 " + UUID.randomUUID().toString().substring(0, 8),
                                publicClient, redirectUris, postLogoutRedirectUris, scopes)))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> body = JSON.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() { });
        @SuppressWarnings("unchecked")
        Map<String, Object> created = (Map<String, Object>) body.get("client");
        return new Client(((Number) created.get("id")).longValue(),
                (String) created.get("clientId"), (String) body.get("clientSecret"));
    }

    private static String clientJson(String name, boolean publicClient, List<String> redirectUris,
                                     List<String> postLogoutRedirectUris, List<String> scopes) throws Exception {
        return JSON.writeValueAsString(Map.of(
                "name", name,
                "description", "多平台 OAuth 集成测试",
                "redirectUris", redirectUris,
                "postLogoutRedirectUris", postLogoutRedirectUris,
                "scopes", scopes,
                "publicClient", publicClient
        ));
    }

    private String register(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@gmail.com","password":"Password123"}
                                """.formatted(username, username)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String login(String identifier, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(identifier, password)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static String uniqueUsername(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static Map<String, String> queryParameters(String url) {
        Map<String, String> result = new HashMap<>();
        String query = URI.create(url).getRawQuery();
        if (query == null || query.isBlank()) {
            return result;
        }
        for (String pair : query.split("&")) {
            int separator = pair.indexOf('=');
            String name = URLDecoder.decode(separator < 0 ? pair : pair.substring(0, separator),
                    StandardCharsets.UTF_8);
            String value = separator < 0 ? "" : URLDecoder.decode(pair.substring(separator + 1),
                    StandardCharsets.UTF_8);
            result.put(name, value);
        }
        return result;
    }

    private static Map<String, Object> jwtClaims(String token) throws Exception {
        byte[] payload = Base64.getUrlDecoder().decode(token.split("\\.")[1]);
        return JSON.readValue(payload, new TypeReference<>() { });
    }

    private static String randomUrlToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String base64UrlSha256(String value) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));
    }

    private record Client(Long id, String clientId, String clientSecret) {
    }

    private record Tokens(String idToken, String refreshToken) {
    }
}
