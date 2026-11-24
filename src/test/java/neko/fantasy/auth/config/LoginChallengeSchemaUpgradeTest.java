package neko.fantasy.auth.config;

import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class LoginChallengeSchemaUpgradeTest {

    @Test
    void widensLegacyDeviceInfoColumnAndIsIdempotent() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:login_challenge_upgrade", "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("create table login_challenges (device_info varchar(255))");
            statement.execute("insert into login_challenges values ('legacy')");

            LoginChallengeSchemaUpgrade.widenDeviceInfo(connection);
            LoginChallengeSchemaUpgrade.widenDeviceInfo(connection);

            String longDeviceInfo = "x".repeat(10_000);
            try (var update = connection.prepareStatement(
                    "update login_challenges set device_info = ?")) {
                update.setString(1, longDeviceInfo);
                assertThat(update.executeUpdate()).isEqualTo(1);
            }
            try (var query = connection.createStatement();
                 var rows = query.executeQuery(
                         "select length(device_info) from login_challenges")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(longDeviceInfo.length());
            }
        }
    }
}
