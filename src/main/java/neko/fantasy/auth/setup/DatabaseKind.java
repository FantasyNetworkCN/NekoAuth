package neko.fantasy.auth.setup;

import java.util.Locale;

enum DatabaseKind {

    MYSQL("jdbc:mysql://", "com.mysql.cj.jdbc.Driver"),
    MARIADB("jdbc:mariadb://", "org.mariadb.jdbc.Driver");

    private final String urlPrefix;
    private final String driverClassName;

    DatabaseKind(String urlPrefix, String driverClassName) {
        this.urlPrefix = urlPrefix;
        this.driverClassName = driverClassName;
    }

    String urlPrefix() {
        return urlPrefix;
    }

    String driverClassName() {
        return driverClassName;
    }

    static DatabaseKind fromVersion(String version) {
        return version != null && version.toLowerCase(Locale.ROOT).contains("mariadb")
                ? MARIADB : MYSQL;
    }
}
