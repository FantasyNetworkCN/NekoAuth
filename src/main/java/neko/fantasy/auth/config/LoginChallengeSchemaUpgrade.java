package neko.fantasy.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Locale;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class LoginChallengeSchemaUpgrade implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LoginChallengeSchemaUpgrade.class);
    private static final long MIN_DEVICE_INFO_LENGTH = 65_535;

    private final DataSource dataSource;

    LoginChallengeSchemaUpgrade(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            widenDeviceInfo(connection);
        }
    }

    static void widenDeviceInfo(Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String database = metadata.getDatabaseProductName().toLowerCase(Locale.ROOT);
        String sql = switch (database) {
            case "mysql", "mariadb" ->
                    "ALTER TABLE login_challenges MODIFY COLUMN device_info LONGTEXT NULL";
            case "postgresql" ->
                    "ALTER TABLE login_challenges ALTER COLUMN device_info TYPE TEXT";
            case "h2" ->
                    "ALTER TABLE login_challenges ALTER COLUMN device_info SET DATA TYPE CLOB";
            default -> null;
        };
        if (sql == null || !deviceInfoIsTooSmall(connection, metadata)) {
            return;
        }
        // Hibernate's schema update does not widen columns that were created by older versions.
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
        log.info("已将 login_challenges.device_info 扩展为可保存完整设备画像的类型");
    }

    private static boolean deviceInfoIsTooSmall(Connection connection, DatabaseMetaData metadata)
            throws SQLException {
        boolean uppercase = metadata.storesUpperCaseIdentifiers();
        String table = uppercase ? "LOGIN_CHALLENGES" : "login_challenges";
        String column = uppercase ? "DEVICE_INFO" : "device_info";
        try (var columns = metadata.getColumns(
                connection.getCatalog(), connection.getSchema(), table, column)) {
            if (!columns.next()) {
                return false;
            }
            long size = columns.getLong("COLUMN_SIZE");
            return size > 0 && size < MIN_DEVICE_INFO_LENGTH;
        }
    }
}
