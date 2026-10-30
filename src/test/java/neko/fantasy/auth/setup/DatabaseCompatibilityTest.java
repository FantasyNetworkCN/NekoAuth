package neko.fantasy.auth.setup;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseCompatibilityTest {

    @Test
    void detectsMariaDbFromServerVersion() {
        assertThat(DatabaseKind.fromVersion("11.8.9-MariaDB-ubu2404")).isEqualTo(DatabaseKind.MARIADB);
        assertThat(DatabaseKind.fromVersion("8.4.6")).isEqualTo(DatabaseKind.MYSQL);
    }

    @Test
    void convertsLegacyMysqlUrlToMariaDbOptions() {
        String converted = DatabaseCompatibility.toMariaDbUrl(
                "jdbc:mysql://127.0.0.1:3306/nekoauth"
                        + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC"
                        + "&connectTimeout=5000&socketTimeout=5000&allowPublicKeyRetrieval=true&sslMode=PREFERRED");

        assertThat(converted)
                .startsWith("jdbc:mariadb://127.0.0.1:3306/nekoauth?")
                .contains("timezone=UTC")
                .contains("connectTimeout=5000")
                .doesNotContain("serverTimezone=")
                .doesNotContain("sslMode=");
    }
}
