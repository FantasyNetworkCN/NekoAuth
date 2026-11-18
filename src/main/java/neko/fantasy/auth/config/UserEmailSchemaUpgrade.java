package neko.fantasy.auth.config;

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
class UserEmailSchemaUpgrade implements ApplicationRunner {

    private final DataSource dataSource;

    UserEmailSchemaUpgrade(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            allowPhoneAccounts(connection);
        }
    }

    static void allowPhoneAccounts(Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String database = metadata.getDatabaseProductName().toLowerCase(Locale.ROOT);
        String sql = switch (database) {
            case "h2", "postgresql" -> "ALTER TABLE users ALTER COLUMN email DROP NOT NULL";
            case "mysql", "mariadb" -> "ALTER TABLE users MODIFY COLUMN email VARCHAR(254) NULL";
            default -> null;
        };
        if (sql == null || !legacyEmailIsRequired(connection, metadata)) {
            return;
        }
        // Hibernate update adds the phone column but does not relax the existing email constraint.
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static boolean legacyEmailIsRequired(Connection connection, DatabaseMetaData metadata) throws SQLException {
        boolean uppercase = metadata.storesUpperCaseIdentifiers();
        String table = uppercase ? "USERS" : "users";
        String column = uppercase ? "EMAIL" : "email";
        try (var columns = metadata.getColumns(connection.getCatalog(), connection.getSchema(), table, column)) {
            return columns.next() && columns.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls;
        }
    }
}
