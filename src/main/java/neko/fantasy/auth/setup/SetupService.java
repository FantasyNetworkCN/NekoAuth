package neko.fantasy.auth.setup;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import neko.fantasy.auth.config.AuthProperties;
import neko.fantasy.auth.exception.ApiException;
import neko.fantasy.auth.service.PasswordPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class SetupService {

    private static final Logger log = LoggerFactory.getLogger(SetupService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String CONFIG_FILE = "nekoauth-setup.yml";
    private static final String COMPLETE_MARKER = ".setup-complete";
    private static final String MYSQL_URL_SUFFIX =
            "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC"
                    + "&connectTimeout=5000&socketTimeout=5000&allowPublicKeyRetrieval=true&sslMode=PREFERRED";
    private static final String MARIADB_URL_SUFFIX =
            "?useUnicode=true&characterEncoding=UTF-8&timezone=UTC"
                    + "&connectTimeout=5000&socketTimeout=5000&allowPublicKeyRetrieval=true";
    private static final Set<PosixFilePermission> PRIVATE_FILE_PERMISSIONS =
            PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> PRIVATE_DIRECTORY_PERMISSIONS =
            PosixFilePermissions.fromString("rwx------");

    private final AuthProperties properties;
    private final PasswordPolicy passwordPolicy;
    private final String setupToken;
    private final ReentrantLock installLock = new ReentrantLock();

    public SetupService(AuthProperties properties, PasswordPolicy passwordPolicy) {
        this.properties = properties;
        this.passwordPolicy = passwordPolicy;
        String configuredToken = properties.setup() == null ? "" : properties.setup().token();
        this.setupToken = StringUtils.hasText(configuredToken) ? configuredToken.trim() : randomToken();
        if (!StringUtils.hasText(configuredToken) && !isComplete()) {
            log.warn("NekoAuth 尚未完成安装。一次性安装令牌: {}", setupToken);
            log.warn("打开 /setup 并在页面中填写此令牌；安装完成后令牌立即失效。");
        }
    }

    public boolean isComplete() {
        return properties.setup() != null && !properties.setup().enabled()
                || Files.isRegularFile(stateDirectory().resolve(COMPLETE_MARKER));
    }

    public SetupResponses.Status status() {
        ensureIncomplete();
        return new SetupResponses.Status(true, true);
    }

    public SetupResponses.Result install(String providedToken, SetupRequests.Install request) {
        verifyToken(providedToken);
        installLock.lock();
        try {
            ensureIncomplete();

            AdminConfig admin = validateAdmin(request);
            DatabaseConfig database = validateMysql(request);
            RedisConfig redis = validateRedis(request);

            database = detectDatabaseKind(database);
            verifyMysql(database);
            verifyRedis(redis);
            writeConfiguration(admin, database, redis);

            log.info("NekoAuth 安装配置已保存，重启服务后将启用 MySQL/MariaDB 和 Redis");
            return new SetupResponses.Result(
                    admin.username(),
                    true,
                    "安装配置已保存。请重启 NekoAuth 服务，重启后将使用 MySQL/MariaDB 和 Redis，/setup 将永久返回 404。"
            );
        } finally {
            installLock.unlock();
        }
    }

    private void ensureIncomplete() {
        if (isComplete()) {
            throw ApiException.notFound("SETUP_NOT_FOUND", "请求的资源不存在");
        }
    }

    private void verifyToken(String providedToken) {
        if (providedToken == null
                || !MessageDigest.isEqual(
                        setupToken.getBytes(StandardCharsets.UTF_8),
                        providedToken.trim().getBytes(StandardCharsets.UTF_8))) {
            throw ApiException.unauthorized("INVALID_SETUP_TOKEN", "安装令牌不正确");
        }
    }

    private AdminConfig validateAdmin(SetupRequests.Install request) {
        String username = request.adminUsername().trim().toLowerCase(Locale.ROOT);
        String email = request.adminEmail().trim().toLowerCase(Locale.ROOT);
        String displayName = StringUtils.hasText(request.adminDisplayName())
                ? request.adminDisplayName().trim() : username;
        passwordPolicy.validate(request.adminPassword());
        return new AdminConfig(
                username,
                email,
                displayName,
                request.adminPassword()
        );
    }

    private DatabaseConfig validateMysql(SetupRequests.Install request) {
        String host = request.mysqlHost().trim();
        if (!host.matches("^[A-Za-z0-9._:\\[\\]-]+$")) {
            throw ApiException.badRequest("INVALID_MYSQL_HOST", "MySQL/MariaDB 主机格式不正确");
        }
        String database = request.mysqlDatabase().trim();
        String username = request.mysqlUsername().trim();
        String password = request.mysqlPassword() == null ? "" : request.mysqlPassword();
        int port = request.mysqlPort();
        String jdbcUrl = jdbcUrl(DatabaseKind.MYSQL, host, port, database);
        return new DatabaseConfig(DatabaseKind.MYSQL, jdbcUrl, username, password, host, port, database);
    }

    private RedisConfig validateRedis(SetupRequests.Install request) {
        String host = request.redisHost().trim();
        if (!host.matches("^[A-Za-z0-9._:\\[\\]-]+$")) {
            throw ApiException.badRequest("INVALID_REDIS_HOST", "Redis 主机格式不正确");
        }
        String password = request.redisPassword() == null ? "" : request.redisPassword();
        return new RedisConfig(host, request.redisPort(), password, request.redisDatabase());
    }

    private void verifyMysql(DatabaseConfig database) {
        try (Connection connection = DriverManager.getConnection(
                database.jdbcUrl(), database.username(), database.password());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1")) {
            if (!resultSet.next()) {
                throw new SQLException("empty result");
            }
        } catch (SQLException exception) {
            throw mysqlConnectionFailure(database, exception);
        }
    }

    private DatabaseConfig detectDatabaseKind(DatabaseConfig database) {
        try (Connection connection = DriverManager.getConnection(
                database.jdbcUrl(), database.username(), database.password());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT VERSION()")) {
            if (!resultSet.next()) {
                throw new SQLException("empty version result");
            }
            DatabaseKind kind = DatabaseKind.fromVersion(resultSet.getString(1));
            if (kind == database.kind()) {
                return database;
            }
            return database.withKind(kind, jdbcUrl(kind, database.host(), database.port(), database.database()));
        } catch (SQLException exception) {
            throw mysqlConnectionFailure(database, exception);
        }
    }

    private static ApiException mysqlConnectionFailure(DatabaseConfig database, SQLException exception) {
        log.warn("MySQL/MariaDB 安装连接测试失败: {}:{} / {} ({})",
                database.host(), database.port(), database.database(), exception.getClass().getSimpleName());
        return ApiException.badRequest(
                "MYSQL_CONNECTION_FAILED",
                "无法连接 MySQL/MariaDB，请检查主机、端口、数据库名和账号密码，并确认数据库已创建"
        );
    }

    private void verifyRedis(RedisConfig redis) {
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(redis.host(), redis.port());
        if (StringUtils.hasText(redis.password())) {
            standalone.setPassword(RedisPassword.of(redis.password()));
        }
        standalone.setDatabase(redis.database());

        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(Duration.ofSeconds(4))
                .build();
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .clientOptions(ClientOptions.builder().socketOptions(socketOptions).build())
                .commandTimeout(Duration.ofSeconds(4))
                .shutdownTimeout(Duration.ofMillis(100))
                .build();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(standalone, clientConfiguration);
        factory.afterPropertiesSet();
        try (RedisConnection connection = factory.getConnection()) {
            String response = connection.ping();
            if (!"PONG".equalsIgnoreCase(response)) {
                throw new IllegalStateException("unexpected ping response");
            }
        } catch (RuntimeException exception) {
            log.warn("Redis 安装连接测试失败: {}:{}", redis.host(), redis.port());
            throw ApiException.badRequest(
                    "REDIS_CONNECTION_FAILED",
                    "无法连接 Redis，请检查主机、端口、数据库编号和密码"
            );
        } finally {
            factory.destroy();
        }
    }

    private void writeConfiguration(AdminConfig admin, DatabaseConfig database, RedisConfig redis) {
        Path stateDirectory = stateDirectory();
        Path configFile = stateDirectory.resolve(CONFIG_FILE);
        Path markerFile = stateDirectory.resolve(COMPLETE_MARKER);
        Path temporaryFile = stateDirectory.resolve(CONFIG_FILE + ".tmp-" + randomToken());
        String yaml = setupYaml(admin, database, redis);
        try {
            Files.createDirectories(stateDirectory);
            setPermissions(stateDirectory, PRIVATE_DIRECTORY_PERMISSIONS);
            writePrivateFile(temporaryFile, yaml.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temporaryFile, configFile,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
            setPermissions(configFile, PRIVATE_FILE_PERMISSIONS);
            writeMarker(markerFile);
        } catch (FileAlreadyExistsException exception) {
            throw ApiException.notFound("SETUP_NOT_FOUND", "请求的资源不存在");
        } catch (IOException exception) {
            log.error("无法写入 NekoAuth 安装配置", exception);
            throw ApiException.badRequest("SETUP_WRITE_FAILED", "安装配置写入失败，请检查数据目录权限");
        } finally {
            try {
                Files.deleteIfExists(temporaryFile);
            } catch (IOException ignored) {
                // A failed cleanup must not hide the original setup result.
            }
        }
    }

    String setupYaml(AdminConfig admin, DatabaseConfig database, RedisConfig redis) {
        StringBuilder yaml = new StringBuilder(1_024);
        yaml.append("spring:\n")
                .append("  datasource:\n")
                .append("    url: ").append(quote(database.jdbcUrl())).append('\n')
                .append("    username: ").append(quote(database.username())).append('\n')
                .append("    password: ").append(quote(database.password())).append('\n')
                .append("    driver-class-name: ").append(quote(database.kind().driverClassName())).append('\n')
                .append("  data:\n")
                .append("    redis:\n")
                .append("      host: ").append(quote(redis.host())).append('\n')
                .append("      port: ").append(redis.port()).append('\n')
                .append("      database: ").append(redis.database()).append('\n')
                .append("      timeout: 3s\n");
        if (StringUtils.hasText(redis.password())) {
            yaml.append("      password: ").append(quote(redis.password())).append('\n');
        }
        yaml.append("auth:\n")
                .append("  jwt:\n")
                .append("    secret: ").append(quote(randomToken())).append('\n')
                .append("  bootstrap-admin:\n")
                .append("    username: ").append(quote(admin.username())).append('\n')
                .append("    email: ").append(quote(admin.email())).append('\n')
                .append("    display-name: ").append(quote(admin.displayName())).append('\n')
                .append("    password: ").append(quote(admin.password())).append('\n');
        return yaml.toString();
    }

    private String quote(String value) {
        StringBuilder quoted = new StringBuilder(value.length() + 2);
        quoted.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (character < 0x20) {
                        quoted.append(String.format("\\u%04x", (int) character));
                    } else {
                        quoted.append(character);
                    }
                }
            }
        }
        return quoted.append('"').toString();
    }

    private Path stateDirectory() {
        String configured = properties.setup() == null ? "./data" : properties.setup().stateDir();
        return Path.of(StringUtils.hasText(configured) ? configured : "./data")
                .toAbsolutePath()
                .normalize();
    }

    private static void writePrivateFile(Path path, byte[] content) throws IOException {
        try (FileChannel channel = FileChannel.open(
                path,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        )) {
            channel.write(ByteBuffer.wrap(content));
            channel.force(true);
        }
        setPermissions(path, PRIVATE_FILE_PERMISSIONS);
    }

    private static void writeMarker(Path markerFile) throws IOException {
        byte[] content = ("installedAt=" + Instant.now() + "\n").getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(
                markerFile,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        )) {
            channel.write(ByteBuffer.wrap(content));
            channel.force(true);
        }
        setPermissions(markerFile, PRIVATE_FILE_PERMISSIONS);
    }

    private static void setPermissions(Path path, Set<PosixFilePermission> permissions) {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException | IOException ignored) {
            // POSIX permissions are unavailable on some systems (for example Windows).
        }
    }

    private static String bracketIpv6(String host) {
        return host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
    }

    private static String jdbcUrl(DatabaseKind kind, String host, int port, String database) {
        String suffix = kind == DatabaseKind.MARIADB ? MARIADB_URL_SUFFIX : MYSQL_URL_SUFFIX;
        return kind.urlPrefix() + bracketIpv6(host) + ":" + port + "/" + database + suffix;
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    record AdminConfig(String username, String email, String displayName, String password) {
    }

    record DatabaseConfig(DatabaseKind kind, String jdbcUrl, String username, String password,
                          String host, int port, String database) {

        DatabaseConfig withKind(DatabaseKind newKind, String newJdbcUrl) {
            return new DatabaseConfig(newKind, newJdbcUrl, username, password, host, port, database);
        }
    }

    record RedisConfig(String host, int port, String password, int database) {
    }
}
