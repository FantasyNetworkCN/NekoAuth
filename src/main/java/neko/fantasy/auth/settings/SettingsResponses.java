package neko.fantasy.auth.settings;

import java.time.Instant;
import java.util.List;

public final class SettingsResponses {

    private SettingsResponses() {
    }

    public record Settings(
            String serviceName,
            boolean registrationEnabled,
            List<String> emailDomainWhitelist,
            Instant updatedAt,
            Smtp smtp
    ) {
    }

    public record Smtp(
            boolean enabled,
            String host,
            int port,
            String username,
            boolean passwordConfigured,
            MailSecurity security,
            boolean authEnabled,
            String fromEmail,
            String fromName,
            int connectionTimeoutMs,
            int readTimeoutMs,
            int writeTimeoutMs
    ) {
    }

    public record RegistrationPolicy(boolean enabled, List<String> emailDomains) {
    }

    public record MailTestResult(String message) {
    }
}
