package neko.fantasy.auth.settings;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import neko.fantasy.auth.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final SettingsService settingsService;

    public MailService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public void sendTest(String recipient) {
        SettingsService.SmtpConfig config = settingsService.getSmtpConfig();
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.host());
        sender.setPort(config.port());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        if (config.authEnabled()) {
            sender.setUsername(config.username());
            sender.setPassword(config.password());
        }

        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", Boolean.toString(config.authEnabled()));
        properties.put("mail.smtp.connectiontimeout", Integer.toString(config.connectionTimeoutMs()));
        properties.put("mail.smtp.timeout", Integer.toString(config.readTimeoutMs()));
        properties.put("mail.smtp.writetimeout", Integer.toString(config.writeTimeoutMs()));
        if (config.security() == MailSecurity.STARTTLS) {
            properties.put("mail.smtp.starttls.enable", "true");
            properties.put("mail.smtp.starttls.required", "true");
        } else if (config.security() == MailSecurity.SSL) {
            properties.put("mail.smtp.ssl.enable", "true");
            properties.put("mail.smtp.ssl.checkserveridentity", "true");
        }

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(
                    config.fromEmail(), config.fromName(), StandardCharsets.UTF_8.name()));
            helper.setTo(recipient);
            helper.setSubject(config.serviceName() + " SMTP 测试");
            helper.setText("""
                    这是一封来自 %s 的 SMTP 测试邮件。

                    如果你收到这封邮件，说明后台保存的 SMTP 服务器、认证信息和发件地址均可正常使用。
                    """.formatted(config.serviceName()));
            sender.send(message);
        } catch (MailException | MessagingException | UnsupportedEncodingException exception) {
            log.warn("SMTP 测试邮件发送失败", exception);
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "MAIL_SEND_FAILED",
                    "SMTP 测试邮件发送失败：" + conciseMessage(exception));
        }
    }

    private static String conciseMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        String singleLine = message.replaceAll("\\s+", " ").trim();
        return singleLine.length() > 240 ? singleLine.substring(0, 240) + "…" : singleLine;
    }
}
