package neko.fantasy.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "auth.setup.state-dir=target/test-setup-integration")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SetupIntegrationTest {

    private static final Path STATE_DIRECTORY = Path.of("target/test-setup-integration");

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    @AfterEach
    void cleanState() throws Exception {
        Files.deleteIfExists(STATE_DIRECTORY.resolve(".setup-complete"));
        Files.deleteIfExists(STATE_DIRECTORY.resolve("nekoauth-setup.yml"));
    }

    @Test
    void setupStatusIsAvailableBeforeInstallation() throws Exception {
        mockMvc.perform(get("/api/setup/status"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.required").value(true))
                .andExpect(jsonPath("$.tokenRequired").value(true));
    }

    @Test
    void invalidSetupTokenIsRejectedBeforeConnectingToServices() throws Exception {
        mockMvc.perform(post("/api/setup/install")
                        .header("X-Setup-Token", "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(installRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SETUP_TOKEN"));
    }

    @Test
    void setupEndpointsReturnRealNotFoundAfterCompletion() throws Exception {
        Files.createDirectories(STATE_DIRECTORY);
        Files.writeString(STATE_DIRECTORY.resolve(".setup-complete"), "installed\n");

        mockMvc.perform(get("/setup"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));

        mockMvc.perform(get("/api/setup/status"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private static String installRequest() {
        return """
                {
                  "adminUsername":"admin",
                  "adminEmail":"admin@example.com",
                  "adminDisplayName":"Neko Admin",
                  "adminPassword":"AdminPass123",
                  "mysqlHost":"127.0.0.1",
                  "mysqlPort":3306,
                  "mysqlDatabase":"nekoauth",
                  "mysqlUsername":"nekoauth",
                  "mysqlPassword":"secret",
                  "redisHost":"127.0.0.1",
                  "redisPort":6379,
                  "redisPassword":"",
                  "redisDatabase":0
                }
                """;
    }
}
