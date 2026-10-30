package neko.fantasy.auth.settings;

import neko.fantasy.auth.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class SettingsService {

    public static final List<String> DEFAULT_EMAIL_DOMAINS = List.of(
            "qq.com",
            "gmail.com",
            "163.com",
            "139.com",
            "126.com",
            "outlook.com",
            "hotmail.com",
            "icloud.com"
    );

    private static final String DEFAULT_SERVICE_NAME = "NekoAuth";
    private static final int DEFAULT_SMTP_PORT = 587;
    private static final int DEFAULT_TIMEOUT_MS = 10_000;
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+"
                    + "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");

    private final ApplicationSettingsRepository repository;
    private final SettingSecretCipher secretCipher;
    private final boolean emailVerificationRequired;

    public SettingsService(ApplicationSettingsRepository repository, SettingSecretCipher secretCipher,
                           @Value("${auth.registration.require-email-verification:true}")
                           boolean emailVerificationRequired) {
        this.repository = repository;
        this.secretCipher = secretCipher;
        this.emailVerificationRequired = emailVerificationRequired;
    }

    @Transactional
    public SettingsResponses.Settings getSettings() {
        return toResponse(loadOrCreate());
    }

    @Transactional
    public SettingsResponses.RegistrationPolicy getRegistrationPolicy() {
        ApplicationSettings settings = loadOrCreate();
        return new SettingsResponses.RegistrationPolicy(
                settings.isRegistrationEnabled(), emailVerificationRequired,
                readDomains(settings.getEmailDomainWhitelist()));
    }

    @Transactional
    public void requireRegistrationAllowed(String email) {
        ApplicationSettings settings = loadOrCreate();
        if (!settings.isRegistrationEnabled()) {
            throw ApiException.forbidden("REGISTRATION_DISABLED", "当前暂未开放新用户注册");
        }

        int separator = email.lastIndexOf('@');
        String domain = separator >= 0 ? email.substring(separator + 1) : "";
        List<String> allowedDomains = readDomains(settings.getEmailDomainWhitelist());
        if (!allowedDomains.contains(domain)) {
            throw ApiException.badRequest(
                    "EMAIL_DOMAIN_NOT_ALLOWED",
                    "该邮箱域名不在注册白名单中，支持：" + String.join("、", allowedDomains));
        }
    }

    @Transactional
    public SettingsResponses.Settings update(SettingsRequests.Update request) {
        ApplicationSettings settings = loadOrCreate();
        List<String> domains = normalizeDomains(request.emailDomainWhitelist());
        SettingsRequests.Smtp smtp = request.smtp();
        String host = trimToEmpty(smtp.host());
        String username = trimToEmpty(smtp.username());
        String fromEmail = normalizeEmail(smtp.fromEmail());
        String newPassword = trimToNull(smtp.password());

        ensureSingleLine(request.serviceName(), "服务名称");
        ensureSingleLine(host, "SMTP 服务器");
        ensureSingleLine(username, "SMTP 用户名");
        ensureSingleLine(smtp.fromName(), "发件人名称");

        if (smtp.enabled()) {
            if (host.isEmpty()) {
                throw ApiException.badRequest("SMTP_HOST_REQUIRED", "启用 SMTP 时必须填写服务器地址");
            }
            if (fromEmail.isEmpty()) {
                throw ApiException.badRequest("SMTP_FROM_REQUIRED", "启用 SMTP 时必须填写发件地址");
            }
            if (smtp.authEnabled() && username.isEmpty()) {
                throw ApiException.badRequest("SMTP_USERNAME_REQUIRED", "启用 SMTP 认证时必须填写用户名");
            }
        }
        if (!fromEmail.isEmpty() && !fromEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw ApiException.badRequest("SMTP_FROM_INVALID", "发件地址格式不正确");
        }
        if (smtp.clearPassword() && newPassword != null) {
            throw ApiException.badRequest("SMTP_PASSWORD_CONFLICT", "不能同时设置和清除 SMTP 密码");
        }
        if (smtp.enabled() && smtp.authEnabled() && smtp.clearPassword()) {
            throw ApiException.badRequest("SMTP_PASSWORD_REQUIRED", "启用 SMTP 认证时必须保留或填写密码");
        }
        if (smtp.enabled() && smtp.authEnabled() && newPassword == null
                && !StringUtils.hasText(settings.getSmtpPasswordCiphertext())) {
            throw ApiException.badRequest("SMTP_PASSWORD_REQUIRED", "启用 SMTP 认证时必须填写密码");
        }

        settings.setServiceName(request.serviceName().trim());
        settings.setRegistrationEnabled(request.registrationEnabled());
        settings.setEmailDomainWhitelist(String.join("\n", domains));
        settings.setSmtpEnabled(smtp.enabled());
        settings.setSmtpHost(host);
        settings.setSmtpPort(smtp.port());
        settings.setSmtpUsername(username);
        settings.setSmtpSecurity(smtp.security());
        settings.setSmtpAuthEnabled(smtp.authEnabled());
        settings.setMailFromEmail(fromEmail);
        settings.setMailFromName(trimToEmpty(smtp.fromName()));
        settings.setSmtpConnectionTimeoutMs(smtp.connectionTimeoutMs());
        settings.setSmtpReadTimeoutMs(smtp.readTimeoutMs());
        settings.setSmtpWriteTimeoutMs(smtp.writeTimeoutMs());

        if (newPassword != null) {
            settings.setSmtpPasswordCiphertext(secretCipher.encrypt(newPassword));
        } else if (smtp.clearPassword()) {
            settings.setSmtpPasswordCiphertext(null);
        }

        return toResponse(repository.saveAndFlush(settings));
    }

    @Transactional
    public SmtpConfig getSmtpConfig() {
        ApplicationSettings settings = loadOrCreate();
        if (!settings.isSmtpEnabled()) {
            throw ApiException.conflict("SMTP_DISABLED", "SMTP 发件功能尚未启用");
        }
        if (!StringUtils.hasText(settings.getSmtpHost()) || !StringUtils.hasText(settings.getMailFromEmail())) {
            throw ApiException.conflict("SMTP_NOT_CONFIGURED", "SMTP 配置不完整，请先保存服务器地址和发件地址");
        }

        String password = null;
        if (settings.isSmtpAuthEnabled()) {
            if (!StringUtils.hasText(settings.getSmtpPasswordCiphertext())) {
                throw ApiException.conflict("SMTP_PASSWORD_REQUIRED", "SMTP 认证已启用，但没有保存密码");
            }
            try {
                password = secretCipher.decrypt(settings.getSmtpPasswordCiphertext());
            } catch (IllegalStateException exception) {
                throw ApiException.conflict(
                        "SMTP_PASSWORD_UNREADABLE", "SMTP 密码无法解密，请在设置页重新填写密码");
            }
        }
        return new SmtpConfig(
                settings.getServiceName(),
                settings.getSmtpHost(),
                settings.getSmtpPort(),
                settings.getSmtpUsername(),
                password,
                settings.getSmtpSecurity(),
                settings.isSmtpAuthEnabled(),
                settings.getMailFromEmail(),
                settings.getMailFromName(),
                settings.getSmtpConnectionTimeoutMs(),
                settings.getSmtpReadTimeoutMs(),
                settings.getSmtpWriteTimeoutMs()
        );
    }

    private ApplicationSettings loadOrCreate() {
        return repository.findById(ApplicationSettings.SINGLETON_ID)
                .orElseGet(() -> repository.save(defaults()));
    }

    private static ApplicationSettings defaults() {
        ApplicationSettings settings = new ApplicationSettings();
        settings.setId(ApplicationSettings.SINGLETON_ID);
        settings.setServiceName(DEFAULT_SERVICE_NAME);
        settings.setRegistrationEnabled(true);
        settings.setEmailDomainWhitelist(String.join("\n", DEFAULT_EMAIL_DOMAINS));
        settings.setSmtpEnabled(false);
        settings.setSmtpHost("");
        settings.setSmtpPort(DEFAULT_SMTP_PORT);
        settings.setSmtpUsername("");
        settings.setSmtpSecurity(MailSecurity.STARTTLS);
        settings.setSmtpAuthEnabled(true);
        settings.setMailFromEmail("");
        settings.setMailFromName(DEFAULT_SERVICE_NAME);
        settings.setSmtpConnectionTimeoutMs(DEFAULT_TIMEOUT_MS);
        settings.setSmtpReadTimeoutMs(DEFAULT_TIMEOUT_MS);
        settings.setSmtpWriteTimeoutMs(DEFAULT_TIMEOUT_MS);
        return settings;
    }

    private static SettingsResponses.Settings toResponse(ApplicationSettings settings) {
        return new SettingsResponses.Settings(
                settings.getServiceName(),
                settings.isRegistrationEnabled(),
                readDomains(settings.getEmailDomainWhitelist()),
                settings.getUpdatedAt(),
                new SettingsResponses.Smtp(
                        settings.isSmtpEnabled(),
                        settings.getSmtpHost(),
                        settings.getSmtpPort(),
                        settings.getSmtpUsername(),
                        StringUtils.hasText(settings.getSmtpPasswordCiphertext()),
                        settings.getSmtpSecurity(),
                        settings.isSmtpAuthEnabled(),
                        settings.getMailFromEmail(),
                        settings.getMailFromName(),
                        settings.getSmtpConnectionTimeoutMs(),
                        settings.getSmtpReadTimeoutMs(),
                        settings.getSmtpWriteTimeoutMs()
                )
        );
    }

    private static List<String> readDomains(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        List<String> domains = new ArrayList<>();
        for (String line : value.split("\\R")) {
            String domain = line.trim().toLowerCase(Locale.ROOT);
            if (!domain.isEmpty()) {
                domains.add(domain);
            }
        }
        return List.copyOf(domains);
    }

    private static List<String> normalizeDomains(List<String> values) {
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            String domain = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            if (domain.startsWith("@")) {
                domain = domain.substring(1);
            }
            if (!DOMAIN_PATTERN.matcher(domain).matches()) {
                throw ApiException.badRequest(
                        "INVALID_EMAIL_DOMAIN", "邮箱域名格式不正确：" + value);
            }
            unique.add(domain);
        }
        if (unique.isEmpty()) {
            throw ApiException.badRequest("EMAIL_DOMAIN_REQUIRED", "至少需要保留一个邮箱域名");
        }
        return List.copyOf(unique);
    }

    private static String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static void ensureSingleLine(String value, String label) {
        if (value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0)) {
            throw ApiException.badRequest("INVALID_SETTING_VALUE", label + "不能包含换行符");
        }
    }

    public record SmtpConfig(
            String serviceName,
            String host,
            int port,
            String username,
            String password,
            MailSecurity security,
            boolean authEnabled,
            String fromEmail,
            String fromName,
            int connectionTimeoutMs,
            int readTimeoutMs,
            int writeTimeoutMs
    ) {
    }
}
