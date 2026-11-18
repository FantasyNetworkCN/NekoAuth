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
            Smtp smtp,
            Sms sms
    ) {
    }

    public record Sms(
            boolean enabled,
            String accessKeyId,
            boolean accessKeySecretConfigured,
            String signName,
            String templateCode,
            String schemeName,
            String codeParameter,
            String minutesParameter
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

    public record RegistrationPolicy(
            boolean enabled,
            boolean emailVerificationRequired,
            boolean smsRegistrationEnabled,
            List<String> emailDomains,
            long emailCodeExpiresInSeconds,
            long emailCodeResendAfterSeconds,
            int emailCodeDailyLimit,
            long emailCodeWindowSeconds
    ) {
    }

    public record MailTestResult(String message) {
    }
}
