package neko.fantasy.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
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
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OAuthFlowIntegrationTest {

    private static final String REDIRECT_URI = "https://fantasy.example/oauth/callback";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void acceptsGithubEmailScopeAlias() throws Exception {
        String adminToken = login("admin", "AdminPass123");
        String clientId = createPublicClient(adminToken);
        String userToken = register("alias_" + UUID.randomUUID().toString().substring(0, 8));
        String verifier = randomUrlToken(48);
        String challenge = base64UrlSha256(verifier);

        mockMvc.perform(get("/api/oauth/authorize")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("client_id", clientId)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("response_type", "code")
                        .param("scope", "user:email")
                        .param("code_challenge", challenge)
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopes[0]").value("email"))
                .andExpect(jsonPath("$.scopeDetails[0].name").value("email"));
    }

    @Test
    void publicClientAuthorizationCodePkceOidcRefreshAndRevocationWork() throws Exception {
        String adminToken = login("admin", "AdminPass123");
        String clientId = createPublicClient(adminToken);

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", clientId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));

        String username = "oauth_" + UUID.randomUUID().toString().substring(0, 8);
        String userToken = register(username);
        String verifier = randomUrlToken(48);
        String challenge = base64UrlSha256(verifier);
        String state = "state-" + UUID.randomUUID();
        String nonce = "nonce-" + UUID.randomUUID();

        mockMvc.perform(get("/api/oauth/authorize")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("client_id", clientId)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("response_type", "code")
                        .param("scope", "openid profile email offline_access")
                        .param("state", state)
                        .param("nonce", nonce)
                        .param("code_challenge", challenge)
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientName").value("Fantasy 测试应用"))
                .andExpect(jsonPath("$.redirectUrl").doesNotExist())
                .andExpect(jsonPath("$.scopeDetails.length()").value(4));

        MvcResult decision = mockMvc.perform(post("/api/oauth/authorize/decision")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientId":"%s",
                                  "redirectUri":"%s",
                                  "responseType":"code",
                                  "scope":"openid profile email offline_access",
                                  "state":"%s",
                                  "nonce":"%s",
                                  "codeChallenge":"%s",
                                  "codeChallengeMethod":"S256",
                                  "approved":true
                                }
                                """.formatted(clientId, REDIRECT_URI, state, nonce, challenge)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectUrl").isNotEmpty())
                .andReturn();

        String redirectUrl = JsonPath.read(decision.getResponse().getContentAsString(), "$.redirectUrl");
        Map<String, String> redirectParameters = queryParameters(redirectUrl);
        assertThat(redirectParameters.get("state")).isEqualTo(state);
        assertThat(redirectParameters.get("iss")).isEqualTo("https://auth.fantasy.example");
        String code = redirectParameters.get("code");
        assertThat(code).isNotBlank();

        MvcResult tokenResult = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", clientId)
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_verifier", verifier))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.id_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andExpect(jsonPath("$.scope").value("openid profile email offline_access"))
                .andReturn();

        String accessToken = JsonPath.read(tokenResult.getResponse().getContentAsString(), "$.access_token");
        String refreshToken = JsonPath.read(tokenResult.getResponse().getContentAsString(), "$.refresh_token");

        mockMvc.perform(get("/oauth2/userinfo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").isNotEmpty())
                .andExpect(jsonPath("$.preferred_username").value(username))
                .andExpect(jsonPath("$.email").value(username + "@example.com"))
                .andExpect(jsonPath("$.email_verified").value(true));

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", clientId)
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_verifier", verifier))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));

        MvcResult refreshed = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("client_id", clientId)
                        .param("refresh_token", refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andReturn();
        String rotatedRefresh = JsonPath.read(refreshed.getResponse().getContentAsString(), "$.refresh_token");
        assertThat(rotatedRefresh).isNotEqualTo(refreshToken);

        mockMvc.perform(post("/oauth2/introspect")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", clientId)
                        .param("token", rotatedRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.token_type").value("refresh_token"));

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("client_id", clientId)
                        .param("refresh_token", refreshToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));

        mockMvc.perform(post("/oauth2/introspect")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", clientId)
                        .param("token", rotatedRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/oauth2/revoke")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", clientId)
                        .param("token", rotatedRefresh))
                .andExpect(status().isOk());

        mockMvc.perform(post("/oauth2/introspect")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", clientId)
                        .param("token", rotatedRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void discoveryJwksAndAuthorizationValidationAreAvailable() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("https://auth.fantasy.example"))
                .andExpect(jsonPath("$.authorization_endpoint").value("https://auth.fantasy.example/oauth2/authorize"))
                .andExpect(jsonPath("$.code_challenge_methods_supported[0]").value("S256"));

        mockMvc.perform(get("/.well-known/oauth-authorization-server"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_endpoint").value("https://auth.fantasy.example/oauth2/token"));

        mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].kid").isNotEmpty());

        mockMvc.perform(get("/api/oauth/authorize")
                        .param("client_id", "unknown")
                        .param("redirect_uri", REDIRECT_URI)
                        .param("response_type", "code"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidBearerTokenUsesAsciiAuthenticationHeader() throws Exception {
        mockMvc.perform(get("/oauth2/userinfo"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""))
                .andExpect(jsonPath("$.error").value("invalid_token"))
                .andExpect(jsonPath("$.error_description").value("缺少访问令牌"));
    }

    @Test
    void confidentialClientUsesBasicAuthenticationAndMinimalDefaultScope() throws Exception {
        String adminToken = login("admin", "AdminPass123");
        ClientCredentials client = createClient(adminToken, false);
        String userToken = register("conf_" + UUID.randomUUID().toString().substring(0, 8));

        mockMvc.perform(get("/api/oauth/authorize")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("client_id", client.clientId())
                        .param("redirect_uri", REDIRECT_URI)
                        .param("response_type", "code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopes.length()").value(1))
                .andExpect(jsonPath("$.scopes[0]").value("openid"))
                .andExpect(jsonPath("$.redirectUrl").doesNotExist());

        String code = codeFor(userToken, client.clientId(), "openid", null);

        mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth(client.clientId(), "wrong-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));

        MvcResult tokenResult = mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth(client.clientId(), client.clientSecret()))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.scope").value("openid"))
                .andReturn();

        String accessToken = JsonPath.read(tokenResult.getResponse().getContentAsString(), "$.access_token");
        Map<String, Object> claims = jwtClaims(accessToken);
        assertThat(claims).containsEntry("typ", "oauth_access").containsEntry("scope", "openid");
        assertThat(claims).doesNotContainKeys("username", "role", "email");
    }

    @Test
    void publicClientAuthorizationValidatesRedirectAndPkce() throws Exception {
        String adminToken = login("admin", "AdminPass123");
        ClientCredentials client = createClient(adminToken, true);
        String userToken = register("pkce_" + UUID.randomUUID().toString().substring(0, 8));
        String verifier = randomUrlToken(48);
        String challenge = base64UrlSha256(verifier);

        mockMvc.perform(get("/api/oauth/authorize")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("client_id", client.clientId())
                        .param("redirect_uri", "https://evil.example/callback")
                        .param("response_type", "code")
                        .param("code_challenge", challenge)
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));

        mockMvc.perform(get("/api/oauth/authorize")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("client_id", client.clientId())
                        .param("redirect_uri", REDIRECT_URI)
                        .param("response_type", "code"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));

        mockMvc.perform(get("/api/oauth/authorize")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("client_id", client.clientId())
                        .param("redirect_uri", REDIRECT_URI)
                        .param("response_type", "code")
                        .param("code_challenge", challenge)
                        .param("code_challenge_method", "plain"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));

        String code = codeFor(userToken, client.clientId(), "openid", challenge);
        String wrongVerifier = "wrong-" + randomUrlToken(32);

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", client.clientId())
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_verifier", wrongVerifier))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", client.clientId())
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_verifier", verifier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty());
    }

    @Test
    void disabledClientCannotExchangeAuthorizationCode() throws Exception {
        String adminToken = login("admin", "AdminPass123");
        ClientCredentials client = createClient(adminToken, true);
        String userToken = register("disabled_" + UUID.randomUUID().toString().substring(0, 8));
        String verifier = randomUrlToken(48);
        String challenge = base64UrlSha256(verifier);
        String code = codeFor(userToken, client.clientId(), "openid", challenge);

        mockMvc.perform(patch("/api/admin/oauth-clients/{id}", client.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", client.clientId())
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_verifier", verifier))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    void adminClientValidationReturnsUnifiedApiErrors() throws Exception {
        String adminToken = login("admin", "AdminPass123");
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/admin/oauth-clients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"非法权限 %s",
                                  "redirectUris":["%s"],
                                  "scopes":["openid","not-a-scope"],
                                  "publicClient":true
                                }
                                """.formatted(suffix, REDIRECT_URI)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SCOPE"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(post("/api/admin/oauth-clients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"明文回调 %s",
                                  "redirectUris":["http://remote.example/callback"],
                                  "scopes":["openid"],
                                  "publicClient":true
                                }
                                """.formatted(suffix)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REDIRECT_URI"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(post("/api/admin/oauth-clients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"本机开发 %s",
                                  "redirectUris":["http://127.0.0.1:3000/callback"],
                                  "scopes":["openid"],
                                  "publicClient":true
                                }
                                """.formatted(suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client.redirectUris[0]").value("http://127.0.0.1:3000/callback"));
    }

    private String createPublicClient(String adminToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/oauth-clients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Fantasy 测试应用",
                                  "description":"端到端测试用公共客户端",
                                  "redirectUris":["%s"],
                                  "scopes":["openid","profile","email","offline_access"],
                                  "publicClient":true
                                }
                                """.formatted(REDIRECT_URI)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client.clientId").isNotEmpty())
                .andExpect(jsonPath("$.client.publicClient").value(true))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.client.clientId");
    }

    private ClientCredentials createClient(String adminToken, boolean publicClient) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult result = mockMvc.perform(post("/api/admin/oauth-clients")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"OAuth 测试应用 %s",
                                  "description":"测试客户端",
                                  "redirectUris":["%s"],
                                  "scopes":["openid","profile"],
                                  "publicClient":%s
                                }
                                """.formatted(suffix, REDIRECT_URI, publicClient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client.clientId").isNotEmpty())
                .andReturn();

        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.client.id");
        String clientId = JsonPath.read(result.getResponse().getContentAsString(), "$.client.clientId");
        String secret = publicClient ? null
                : JsonPath.read(result.getResponse().getContentAsString(), "$.clientSecret");
        return new ClientCredentials(id.longValue(), clientId, secret);
    }

    private String codeFor(String userToken, String clientId, String scope, String challenge) throws Exception {
        String state = "state-" + UUID.randomUUID();
        String challengeJson = challenge == null ? "null" : "\"" + challenge + "\"";
        String challengeMethodJson = challenge == null ? "null" : "\"S256\"";
        MvcResult decision = mockMvc.perform(post("/api/oauth/authorize/decision")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientId":"%s",
                                  "redirectUri":"%s",
                                  "responseType":"code",
                                  "scope":"%s",
                                  "state":"%s",
                                  "codeChallenge":%s,
                                  "codeChallengeMethod":%s,
                                  "approved":true
                                }
                                """.formatted(clientId, REDIRECT_URI, scope, state, challengeJson, challengeMethodJson)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectUrl").isNotEmpty())
                .andReturn();

        String redirectUrl = JsonPath.read(decision.getResponse().getContentAsString(), "$.redirectUrl");
        String code = queryParameters(redirectUrl).get("code");
        assertThat(code).isNotBlank();
        return code;
    }

    private static String basicAuth(String clientId, String secret) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Object> jwtClaims(String token) throws Exception {
        byte[] payload = Base64.getUrlDecoder().decode(token.split("\\.")[1]);
        return JSON.readValue(payload, new TypeReference<>() { });
    }

    private String register(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@example.com","password":"Password123"}
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

    private static Map<String, String> queryParameters(String url) {
        Map<String, String> result = new HashMap<>();
        String query = URI.create(url).getRawQuery();
        for (String pair : query.split("&")) {
            int separator = pair.indexOf('=');
            String name = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
            result.put(name, value);
        }
        return result;
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

    private record ClientCredentials(Long id, String clientId, String clientSecret) {
    }
}
