package neko.fantasy.auth.config;

import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserEmailSchemaUpgradeTest {

    @Test
    void dropsLegacyNotNullWithoutDroppingUniqueness() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:user_email_upgrade", "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("create table users (email varchar(254) not null unique)");
            statement.executeUpdate("insert into users values ('existing@example.com')");

            UserEmailSchemaUpgrade.allowPhoneAccounts(connection);
            UserEmailSchemaUpgrade.allowPhoneAccounts(connection);

            assertThat(statement.executeUpdate("insert into users values (null), (null)")).isEqualTo(2);
            assertThatThrownBy(() -> statement.executeUpdate("insert into users values ('existing@example.com')"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
