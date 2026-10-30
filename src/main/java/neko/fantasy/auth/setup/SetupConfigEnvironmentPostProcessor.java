package neko.fantasy.auth.setup;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SetupConfigEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String CONFIG_FILE = "nekoauth-setup.yml";
    private static final String COMPLETE_MARKER = ".setup-complete";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (Arrays.asList(environment.getActiveProfiles()).contains("test")) {
            return;
        }

        Path stateDirectory = Path.of(environment.getProperty("auth.setup.state-dir", "./data"))
                .toAbsolutePath()
                .normalize();
        Path marker = stateDirectory.resolve(COMPLETE_MARKER);
        Path config = stateDirectory.resolve(CONFIG_FILE);
        if (!Files.isRegularFile(marker) || !Files.isRegularFile(config)) {
            return;
        }

        try {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                    .load("nekoauth-setup", new FileSystemResource(config));
            for (PropertySource<?> source : sources) {
                if (environment.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
                    environment.getPropertySources().addAfter(
                            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
                } else {
                    environment.getPropertySources().addLast(source);
                }
            }
            applyLegacyMariaDbCompatibility(environment);
            ConfigurationPropertySources.attach(environment);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取安装配置文件 " + config, exception);
        }
    }

    private static void applyLegacyMariaDbCompatibility(ConfigurableEnvironment environment) {
        String jdbcUrl = environment.getProperty("spring.datasource.url");
        String driverClassName = environment.getProperty("spring.datasource.driver-class-name");
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:mysql:") || StringUtils.hasText(driverClassName)) {
            return;
        }

        String username = environment.getProperty("spring.datasource.username", "");
        String password = environment.getProperty("spring.datasource.password", "");
        if (!isMariaDb(jdbcUrl, username, password)) {
            return;
        }

        Map<String, Object> overrides = Map.of(
                "spring.datasource.url", DatabaseCompatibility.toMariaDbUrl(jdbcUrl),
                "spring.datasource.driver-class-name", DatabaseKind.MARIADB.driverClassName()
        );
        PropertySource<?> source = new MapPropertySource("nekoauth-legacy-mariadb", overrides);
        if (environment.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            environment.getPropertySources().addAfter(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
        } else {
            environment.getPropertySources().addFirst(source);
        }
    }

    private static boolean isMariaDb(String jdbcUrl, String username, String password) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT VERSION()")) {
            return resultSet.next()
                    && DatabaseKind.fromVersion(resultSet.getString(1)) == DatabaseKind.MARIADB;
        } catch (SQLException exception) {
            return false;
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }
}
