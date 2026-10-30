package neko.fantasy.auth.setup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class DatabaseCompatibility {

    private DatabaseCompatibility() {
    }

    static String toMariaDbUrl(String mysqlUrl) {
        if (!mysqlUrl.startsWith("jdbc:mysql:")) {
            throw new IllegalArgumentException("Not a MySQL JDBC URL");
        }

        String converted = "jdbc:mariadb:" + mysqlUrl.substring("jdbc:mysql:".length());
        int queryStart = converted.indexOf('?');
        if (queryStart < 0) {
            return converted;
        }

        String base = converted.substring(0, queryStart);
        String[] parameters = converted.substring(queryStart + 1).split("&");
        List<String> compatibleParameters = new ArrayList<>(parameters.length);
        for (String parameter : parameters) {
            if (parameter.isBlank()) {
                continue;
            }
            int separator = parameter.indexOf('=');
            String name = separator < 0 ? parameter : parameter.substring(0, separator);
            String value = separator < 0 ? "" : parameter.substring(separator + 1);
            switch (name.toLowerCase(Locale.ROOT)) {
                case "sslmode" -> {
                    // MariaDB Connector/J has no PREFERRED mode and falls back safely with its default.
                }
                case "servertimezone" -> compatibleParameters.add("timezone=" + value);
                default -> compatibleParameters.add(parameter);
            }
        }
        return compatibleParameters.isEmpty()
                ? base
                : base + "?" + String.join("&", compatibleParameters);
    }
}
