package neko.fantasy.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpaRoutingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rootIsNotExposed() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminPagesServeTheVueApplication() throws Exception {
        for (String path : new String[]{"/admin", "/admin/clients", "/admin/settings"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test
    void oauthEntryPagesStillServeTheVueApplication() throws Exception {
        for (String path : new String[]{"/login", "/register", "/account", "/authorize", "/oauth/consent"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test
    void removedApplicationRoutesReturnNotFound() throws Exception {
        for (String path : new String[]{"/app", "/app/profile", "/app/sessions", "/app/admin"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isNotFound());
        }
    }
}
