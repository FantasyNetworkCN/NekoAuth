package neko.fantasy.auth.setup;

import neko.fantasy.auth.config.AuthProperties;
import org.springframework.util.StringUtils;

import java.nio.file.Path;

public final class SetupState {

    public static final String CONFIG_FILE = "nekoauth-setup.yml";
    public static final String COMPLETE_MARKER = ".setup-complete";
    public static final String PENDING_ADMIN_MARKER = ".admin-bootstrap-pending";

    private SetupState() {
    }

    public static Path directory(AuthProperties properties) {
        String configured = properties.setup() == null ? "./data" : properties.setup().stateDir();
        return Path.of(StringUtils.hasText(configured) ? configured : "./data")
                .toAbsolutePath()
                .normalize();
    }

    public static Path pendingAdminMarker(AuthProperties properties) {
        return directory(properties).resolve(PENDING_ADMIN_MARKER);
    }
}
