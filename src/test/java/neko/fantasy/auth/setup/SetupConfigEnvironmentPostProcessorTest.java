package neko.fantasy.auth.setup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SetupConfigEnvironmentPostProcessorTest {

    @TempDir
    private Path stateDirectory;

    @Test
    void completedSetupOverridesApplicationDefaults() throws Exception {
        Files.writeString(stateDirectory.resolve(".setup-complete"), "installed\n");
        Files.writeString(stateDirectory.resolve("nekoauth-setup.yml"), """
                spring:
                  datasource:
                    url: jdbc:mysql://mysql:3306/nekoauth
                    username: nekoauth
                    password: secret
                    driver-class-name: com.mysql.cj.jdbc.Driver
                auth:
                  setup:
                    state-dir: %s
                """.formatted(stateDirectory));

        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("dev");
        environment.getPropertySources().addLast(new MapPropertySource("application", Map.of(
                "auth.setup.state-dir", stateDirectory.toString(),
                "spring.datasource.url", "jdbc:h2:mem:default"
        )));

        new SetupConfigEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:mysql://mysql:3306/nekoauth");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("nekoauth");
    }
}
