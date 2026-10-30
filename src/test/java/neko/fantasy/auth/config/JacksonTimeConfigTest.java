package neko.fantasy.auth.config;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonTimeConfigTest {

    @Test
    void serializesInstantWithUtcPlusEightOffset() {
        JsonMapper mapper = JsonMapper.builder()
                .addModule(new JacksonTimeConfig().shanghaiInstantModule())
                .build();

        String json = mapper.writeValueAsString(Map.of("at", Instant.parse("2026-10-30T18:00:00Z")));

        assertThat(json).isEqualTo("{\"at\":\"2026-10-31T02:00:00+08:00\"}");
    }
}
