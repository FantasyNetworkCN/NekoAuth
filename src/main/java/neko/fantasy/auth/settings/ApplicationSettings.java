package neko.fantasy.auth.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "application_settings")
public class ApplicationSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "service_name", nullable = false, length = 80)
    private String serviceName;

    @Column(name = "registration_enabled", nullable = false)
    private boolean registrationEnabled;

    @Lob
    @Column(name = "email_domain_whitelist", nullable = false)
    private String emailDomainWhitelist;

    @Column(name = "smtp_enabled", nullable = false)
    private boolean smtpEnabled;

    @Column(name = "smtp_host", nullable = false, length = 255)
    private String smtpHost;

    @Column(name = "smtp_port", nullable = false)
    private int smtpPort;

    @Column(name = "smtp_username", nullable = false, length = 255)
    private String smtpUsername;

    @Column(name = "smtp_password_ciphertext", length = 2048)
    private String smtpPasswordCiphertext;

    @Enumerated(EnumType.STRING)
    @Column(name = "smtp_security", nullable = false, length = 16)
    private MailSecurity smtpSecurity;

    @Column(name = "smtp_auth_enabled", nullable = false)
    private boolean smtpAuthEnabled;

    @Column(name = "mail_from_email", nullable = false, length = 254)
    private String mailFromEmail;

    @Column(name = "mail_from_name", nullable = false, length = 120)
    private String mailFromName;

    @Column(name = "smtp_connection_timeout_ms", nullable = false)
    private int smtpConnectionTimeoutMs;

    @Column(name = "smtp_read_timeout_ms", nullable = false)
    private int smtpReadTimeoutMs;

    @Column(name = "smtp_write_timeout_ms", nullable = false)
    private int smtpWriteTimeoutMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public boolean isRegistrationEnabled() {
        return registrationEnabled;
    }

    public void setRegistrationEnabled(boolean registrationEnabled) {
        this.registrationEnabled = registrationEnabled;
    }

    public String getEmailDomainWhitelist() {
        return emailDomainWhitelist;
    }

    public void setEmailDomainWhitelist(String emailDomainWhitelist) {
        this.emailDomainWhitelist = emailDomainWhitelist;
    }

    public boolean isSmtpEnabled() {
        return smtpEnabled;
    }

    public void setSmtpEnabled(boolean smtpEnabled) {
        this.smtpEnabled = smtpEnabled;
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    public int getSmtpPort() {
        return smtpPort;
    }

    public void setSmtpPort(int smtpPort) {
        this.smtpPort = smtpPort;
    }

    public String getSmtpUsername() {
        return smtpUsername;
    }

    public void setSmtpUsername(String smtpUsername) {
        this.smtpUsername = smtpUsername;
    }

    public String getSmtpPasswordCiphertext() {
        return smtpPasswordCiphertext;
    }

    public void setSmtpPasswordCiphertext(String smtpPasswordCiphertext) {
        this.smtpPasswordCiphertext = smtpPasswordCiphertext;
    }

    public MailSecurity getSmtpSecurity() {
        return smtpSecurity;
    }

    public void setSmtpSecurity(MailSecurity smtpSecurity) {
        this.smtpSecurity = smtpSecurity;
    }

    public boolean isSmtpAuthEnabled() {
        return smtpAuthEnabled;
    }

    public void setSmtpAuthEnabled(boolean smtpAuthEnabled) {
        this.smtpAuthEnabled = smtpAuthEnabled;
    }

    public String getMailFromEmail() {
        return mailFromEmail;
    }

    public void setMailFromEmail(String mailFromEmail) {
        this.mailFromEmail = mailFromEmail;
    }

    public String getMailFromName() {
        return mailFromName;
    }

    public void setMailFromName(String mailFromName) {
        this.mailFromName = mailFromName;
    }

    public int getSmtpConnectionTimeoutMs() {
        return smtpConnectionTimeoutMs;
    }

    public void setSmtpConnectionTimeoutMs(int smtpConnectionTimeoutMs) {
        this.smtpConnectionTimeoutMs = smtpConnectionTimeoutMs;
    }

    public int getSmtpReadTimeoutMs() {
        return smtpReadTimeoutMs;
    }

    public void setSmtpReadTimeoutMs(int smtpReadTimeoutMs) {
        this.smtpReadTimeoutMs = smtpReadTimeoutMs;
    }

    public int getSmtpWriteTimeoutMs() {
        return smtpWriteTimeoutMs;
    }

    public void setSmtpWriteTimeoutMs(int smtpWriteTimeoutMs) {
        this.smtpWriteTimeoutMs = smtpWriteTimeoutMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
