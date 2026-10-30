package neko.fantasy.auth;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import neko.fantasy.auth.domain.User;
import neko.fantasy.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void productionOriginCanSubmitLoginRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://auth.nekocore.cn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"cors-probe@example.com","password":"WrongPass123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "https://auth.nekocore.cn"));
    }

    @Test
    void registerLoginRefreshAndReuseDetectionWork() throws Exception {
        String username = "user_" + UUID.randomUUID().toString().substring(0, 8);
        String email = username + "@gmail.com";

        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s","password":"Password123"}
                                """.formatted(username, email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.username").value(username))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andReturn();

        String accessToken = JsonPath.read(registration.getResponse().getContentAsString(), "$.accessToken");
        Cookie firstRefresh = registration.getResponse().getCookie("refresh_token");
        assertThat(accessToken).isNotBlank();
        assertThat(firstRefresh).isNotNull();

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        MvcResult refresh = mockMvc.perform(post("/api/auth/refresh").cookie(firstRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie secondRefresh = refresh.getResponse().getCookie("refresh_token");
        assertThat(secondRefresh).isNotNull();
        assertThat(secondRefresh.getValue()).isNotEqualTo(firstRefresh.getValue());

        mockMvc.perform(post("/api/auth/refresh").cookie(firstRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_REUSED"));

        mockMvc.perform(post("/api/auth/refresh").cookie(secondRefresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bootstrapAdminStartsWithTheConfiguredUserIdBase() {
        User admin = userRepository.findByUsername("admin").orElseThrow();

        assertThat(admin.getId()).isEqualTo(1_000_000L);
    }

    @Test
    void adminUsesUsernameAndHasNoNickname() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"admin","password":"AdminPass123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("admin"))
                .andExpect(jsonPath("$.user.displayName").value("admin"))
                .andExpect(jsonPath("$.user.nickname").doesNotExist());
    }

    @Test
    void usersStartAtOneMillionAndRegistrationFillsImmutableNickname() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String nickname = "nekomusic_" + suffix;
        String email = nickname + "@gmail.com";

        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"abcdef"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.nickname").value(nickname))
                .andExpect(jsonPath("$.user.displayName").value(nickname))
                .andExpect(jsonPath("$.user.username").isNotEmpty())
                .andReturn();

        Number userId = JsonPath.read(registration.getResponse().getContentAsString(), "$.user.id");
        assertThat(userId.longValue()).isGreaterThanOrEqualTo(1_000_000L);

        User stored = userRepository.findByEmail(email).orElseThrow();
        assertThat(stored.getPasswordHash()).startsWith("$argon2i$v=19$m=65536,t=10,p=1$");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"abcdef"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.nickname").value(nickname));
    }

    @Test
    void derivedNicknameAllowsDuplicatesAndLegacyBcryptUpgradesOnLogin() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String nickname = "duplicate_" + suffix;
        String firstEmail = nickname + "@gmail.com";
        String secondEmail = nickname + "@outlook.com";

        MvcResult first = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"abcdef"}
                                """.formatted(firstEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.displayName").value(nickname))
                .andReturn();
        MvcResult second = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"abcdef"}
                                """.formatted(secondEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.displayName").value(nickname))
                .andReturn();

        String firstUsername = JsonPath.read(first.getResponse().getContentAsString(), "$.user.username");
        String secondUsername = JsonPath.read(second.getResponse().getContentAsString(), "$.user.username");
        assertThat(firstUsername).isNotEqualTo(secondUsername);

        User legacy = new User();
        legacy.setUsername("legacy_" + suffix);
        legacy.setEmail("legacy_" + suffix + "@gmail.com");
        legacy.setDisplayName("旧版用户");
        legacy.setPasswordHash(new BCryptPasswordEncoder(12).encode("Password123"));
        userRepository.saveAndFlush(legacy);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"Password123"}
                                """.formatted(legacy.getEmail())))
                .andExpect(status().isOk());

        assertThat(userRepository.findByEmail(legacy.getEmail()).orElseThrow().getPasswordHash())
                .startsWith("$argon2i$v=19$m=65536,t=10,p=1$");
    }

    @Test
    void loginValidationAndLogoutWork() throws Exception {
        String username = "user_" + UUID.randomUUID().toString().substring(0, 8);
        String email = username + "@gmail.com";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s","password":"short"}
                                """.formatted(username, email)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.password").exists());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s","password":"Password123"}
                                """.formatted(username, email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"WrongPass123"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"Password123"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.lastLoginAt").exists())
                .andReturn();

        Cookie refreshCookie = login.getResponse().getCookie("refresh_token");
        String accessToken = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(patch("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Updated User\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_IMMUTABLE"));

        assertThat(userRepository.findByEmail(email).orElseThrow().getDisplayName())
                .isEqualTo(username);

        mockMvc.perform(post("/api/auth/logout").cookie(refreshCookie))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0));

        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanListUsersButCannotDisableSelf() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"admin\",\"password\":\"AdminPass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andReturn();

        String accessToken = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        Number adminId = JsonPath.read(login.getResponse().getContentAsString(), "$.user.id");

        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[?(@.username == 'admin')]").exists());

        mockMvc.perform(patch("/api/admin/users/{id}", adminId.longValue())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_DISABLE_SELF"));
    }

    @Test
    void changingPasswordInvalidatesAccessAndRefreshTokens() throws Exception {
        String username = "user_" + UUID.randomUUID().toString().substring(0, 8);
        String email = username + "@gmail.com";

        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s","password":"Password123"}
                                """.formatted(username, email)))
                .andExpect(status().isCreated())
                .andReturn();

        String accessToken = JsonPath.read(registration.getResponse().getContentAsString(), "$.accessToken");
        Cookie refreshCookie = registration.getResponse().getCookie("refresh_token");

        mockMvc.perform(post("/api/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Password123\",\"newPassword\":\"NewPassword456\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"NewPassword456"}
                                """.formatted(username)))
                .andExpect(status().isOk());
    }

    @Test
    void sessionsCanBeListedAndRevoked() throws Exception {
        String username = "user_" + UUID.randomUUID().toString().substring(0, 8);
        String email = username + "@gmail.com";

        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s","password":"Password123"}
                                """.formatted(username, email)))
                .andExpect(status().isCreated())
                .andReturn();

        String accessToken = JsonPath.read(registration.getResponse().getContentAsString(), "$.accessToken");
        Cookie refreshCookie = registration.getResponse().getCookie("refresh_token");

        MvcResult sessions = mockMvc.perform(get("/api/auth/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].current").value(true))
                .andReturn();

        Number sessionId = JsonPath.read(sessions.getResponse().getContentAsString(), "$[0].id");
        mockMvc.perform(delete("/api/auth/sessions/{id}", sessionId.longValue())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .cookie(refreshCookie))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0));

        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }
}
