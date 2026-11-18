package neko.fantasy.auth.settings;

import neko.fantasy.auth.config.AuthProperties;
import neko.fantasy.auth.exception.ApiException;
import org.springframework.http.HttpStatus;
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
    private static final String DEFAULT_SMS_CODE_PARAMETER = "code";
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+"
                    + "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");

    private final ApplicationSettingsRepository repository;
    private final SettingSecretCipher secretCipher;
    private final AuthProperties properties;
    private final boolean emailVerificationRequired;

    public SettingsService(ApplicationSettingsRepository repository, SettingSecretCipher secretCipher,
                           AuthProperties properties) {
        this.repository = repository;
        this.secretCipher = secretCipher;
        this.properties = properties;
        this.emailVerificationRequired = properties.registration().requireEmailVerification();
    }

    @Transactional
    public SettingsResponses.Settings getSettings() {
        return toResponse(loadOrCreate());
    }

    @Transactional
    public SettingsResponses.RegistrationPolicy getRegistrationPolicy() {
        ApplicationSettings settings = loadOrCreate();
        AuthProperties.Registration registration = properties.registration();
        return new SettingsResponses.RegistrationPolicy(
                settings.isRegistrationEnabled(), emailVerificationRequired, isSmsEnabled(settings.getSms()),
                readDomains(settings.getEmailDomainWhitelist()),
                registration.emailCodeTtl().toSeconds(),
                registration.emailCodeCooldown().toSeconds(),
                Math.max(1, registration.emailCodeDailyLimit()),
                registration.emailCodeWindow().toSeconds());
    }

    @Transactional
    public void requireRegistrationAllowed(String email) {
        ApplicationSettings settings = loadOrCreate();
        checkRegistrationEnabled(settings);

        int separator = email.lastIndexOf('@');
        String domain = separator >= 0 ? email.substring(separator + 1) : "";
        List<String> allowedDomains = readDomains(settings.getEmailDomainWhitelist());
        if (!allowedDomains.contains(domain)) {
            throw ApiException.badRequest(
                    "EMAIL_DOMAIN_NOT_ALLOWED",
                    "不允许的邮箱");
        }
    }

    @Transactional
    public void requirePhoneRegistrationEnabled() {
        requireSmsSettings(loadOrCreate());
    }

    private static void checkRegistrationEnabled(ApplicationSettings settings) {
        if (!settings.isRegistrationEnabled()) {
            throw ApiException.forbidden("REGISTRATION_DISABLED", "当前暂未开放新用户注册");
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

        if (request.sms() != null) {
            settings.setSms(updateSms(settings.getSms(), request.sms()));
        }

        return toResponse(repository.saveAndFlush(settings));
    }

    private SmsRegistrationSettings updateSms(SmsRegistrationSettings existing, SettingsRequests.Sms request) {
        ensureSingleLine(request.accessKeyId(), "AccessKey ID");
        ensureSingleLine(request.accessKeySecret(), "AccessKey Secret");
        ensureSingleLine(request.signName(), "短信签名");
        ensureSingleLine(request.templateCode(), "短信模板 CODE");
        ensureSingleLine(request.schemeName(), "认证方案名称");
        ensureSingleLine(request.codeParameter(), "验证码变量名");
        ensureSingleLine(request.minutesParameter(), "有效分钟数变量名");

        String newSecret = trimToNull(request.accessKeySecret());
        boolean clearSecret = Boolean.TRUE.equals(request.clearAccessKeySecret());
        if (newSecret != null && clearSecret) {
            throw ApiException.badRequest("SMS_SECRET_CONFLICT", "不能同时设置和清除 AccessKey Secret");
        }

        var sms = new SmsRegistrationSettings();
        sms.enabled = request.enabled();
        sms.accessKeyId = trimToNull(request.accessKeyId());
        sms.signName = trimToNull(request.signName());
        sms.templateCode = trimToNull(request.templateCode());
        sms.schemeName = trimToNull(request.schemeName());
        sms.codeParameter = trimToNull(request.codeParameter());
        sms.minutesParameter = trimToNull(request.minutesParameter());
        if (effectiveCodeParameter(sms).equals(sms.minutesParameter)) {
            throw ApiException.badRequest("SMS_TEMPLATE_PARAMETERS_CONFLICT", "验证码与有效分钟数不能使用同一个模板变量名");
        }

        if (newSecret != null) {
            sms.accessKeySecretCiphertext = secretCipher.encrypt(newSecret);
        } else if (!clearSecret && existing != null) {
            sms.accessKeySecretCiphertext = existing.accessKeySecretCiphertext;
        }
        if (request.enabled() && (sms.accessKeyId == null || sms.signName == null || sms.templateCode == null
                || !StringUtils.hasText(sms.accessKeySecretCiphertext))) {
            throw ApiException.badRequest("SMS_SETTINGS_REQUIRED", "启用手机号注册前必须填写 AccessKey ID、AccessKey Secret、签名和模板 CODE");
        }
        return sms;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public SmsConfig getSmsConfig() {
        var sms = requireSmsSettings(loadOrCreate());
        String secret;
        try {
            secret = secretCipher.decrypt(sms.accessKeySecretCiphertext);
        } catch (IllegalStateException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SMS_SERVICE_UNAVAILABLE", "短信服务暂不可用，请联系管理员");
        }
        return new SmsConfig(sms.accessKeyId, secret, sms.signName, sms.templateCode, sms.schemeName,
                effectiveCodeParameter(sms), sms.minutesParameter);
    }

    private static SmsRegistrationSettings requireSmsSettings(ApplicationSettings settings) {
        checkRegistrationEnabled(settings);
        var sms = settings.getSms();
        if (!isSmsEnabled(sms)) {
            throw ApiException.forbidden("SMS_REGISTRATION_DISABLED", "当前未开放手机号注册");
        }
        return sms;
    }

    private static boolean isSmsEnabled(SmsRegistrationSettings sms) {
        return sms != null && Boolean.TRUE.equals(sms.enabled);
    }

    private static String effectiveCodeParameter(SmsRegistrationSettings sms) {
        return sms.codeParameter == null ? DEFAULT_SMS_CODE_PARAMETER : sms.codeParameter;
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
                ),
                toSmsResponse(settings.getSms())
        );
    }

    private static SettingsResponses.Sms toSmsResponse(SmsRegistrationSettings sms) {
        if (sms == null) {
            return new SettingsResponses.Sms(false, "", false, "", "", "", "", "");
        }
        return new SettingsResponses.Sms(isSmsEnabled(sms), trimToEmpty(sms.accessKeyId),
                StringUtils.hasText(sms.accessKeySecretCiphertext), trimToEmpty(sms.signName),
                trimToEmpty(sms.templateCode), trimToEmpty(sms.schemeName), trimToEmpty(sms.codeParameter),
                trimToEmpty(sms.minutesParameter));
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

    public record SmsConfig(String accessKeyId, String accessKeySecret, String signName, String templateCode,
                            String schemeName, String codeParameter, String minutesParameter) {
        @Override
        public String toString() {
            return "SmsConfig[credentials redacted]";
        }
    }
}
