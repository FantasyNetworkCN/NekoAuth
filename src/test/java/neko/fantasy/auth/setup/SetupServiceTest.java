package neko.fantasy.auth.setup;

import neko.fantasy.auth.config.AuthProperties;
import neko.fantasy.auth.service.PasswordPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SetupServiceTest {

    @TempDir
    private Path stateDirectory;

    @Test
    void setupConfigurationKeepsRawAdminPasswordForBootstrapHashing() {
        AuthProperties properties = new AuthProperties(
                null,
                null,
                null,
                null,
                null,
                null,
                new AuthProperties.Setup(stateDirectory.toString(), "test-token", true)
        );
        SetupService service = new SetupService(properties, new PasswordPolicy());

        String yaml = service.setupYaml(
                new SetupService.AdminConfig("admin", "admin@example.com", "AdminPass123"),
                new SetupService.DatabaseConfig(
                        DatabaseKind.MARIADB,
                        "jdbc:mariadb://mysql:3306/nekoauth", "nekoauth", "db-secret", "mysql", 3306, "nekoauth"),
                new SetupService.RedisConfig("redis", 6379, "redis-secret", 0)
        );

        assertThat(yaml).contains("password: \"AdminPass123\"");
        assertThat(yaml).doesNotContain("$2a$", "$2b$", "$2y$");
        assertThat(yaml)
                .contains("url: \"jdbc:mariadb://mysql:3306/nekoauth\"")
                .contains("driver-class-name: \"org.mariadb.jdbc.Driver\"");
    }

    @Test
    void disablingSetupClosesTheWizardWithoutACompletionMarker() {
        AuthProperties properties = new AuthProperties(
                null,
                null,
                null,
                null,
                null,
                null,
                new AuthProperties.Setup(stateDirectory.toString(), "unused-token", false)
        );

        SetupService service = new SetupService(properties, new PasswordPolicy());

        assertThat(service.isComplete()).isTrue();
    }

    @Test
    void installationWritesOneTimeAdminBootstrapMarker() {
        AuthProperties properties = new AuthProperties(
                null,
                null,
                null,
                null,
                null,
                null,
                new AuthProperties.Setup(stateDirectory.toString(), "test-token", true)
        );
        SetupService service = new SetupService(properties, new PasswordPolicy());

        service.writeConfiguration(
                new SetupService.AdminConfig("admin", "support@nekocore.cn", "AdminPass123"),
                new SetupService.DatabaseConfig(
                        DatabaseKind.MARIADB,
                        "jdbc:mariadb://mysql:3306/nekoauth", "nekoauth", "db-secret", "mysql", 3306, "nekoauth"),
                new SetupService.RedisConfig("redis", 6379, "redis-secret", 0)
        );

        assertThat(stateDirectory.resolve(SetupState.CONFIG_FILE)).isRegularFile();
        assertThat(stateDirectory.resolve(SetupState.PENDING_ADMIN_MARKER)).isRegularFile();
        assertThat(stateDirectory.resolve(SetupState.COMPLETE_MARKER)).isRegularFile();
    }
}
