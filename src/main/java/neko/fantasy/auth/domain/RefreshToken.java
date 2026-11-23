package neko.fantasy.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_tokens_user_active", columnList = "user_id, revoked_at, expires_at"),
        @Index(name = "idx_refresh_tokens_family", columnList = "family_id, revoked_at"),
        @Index(name = "idx_refresh_tokens_expires", columnList = "expires_at")
})
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "auth_time")
    private Instant authTime;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "device_info", length = 8000)
    private String deviceInfo;

    @Column(name = "family_id", length = 64)
    private String familyId;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "risk_level", length = 16)
    private String riskLevel;

    @Column(name = "risk_reasons", length = 1000)
    private String riskReasons;

    @Column(name = "login_country_code", length = 8)
    private String loginCountryCode;

    @Column(name = "login_country_name", length = 80)
    private String loginCountryName;

    @Column(name = "login_region", length = 80)
    private String loginRegion;

    @Column(name = "login_city", length = 80)
    private String loginCity;

    @Column(name = "login_latitude")
    private Double loginLatitude;

    @Column(name = "login_longitude")
    private Double loginLongitude;

    @Column(name = "login_asn")
    private Long loginAsn;

    @Column(name = "login_asn_organization", length = 160)
    private String loginAsnOrganization;

    protected RefreshToken() {
    }

    public RefreshToken(User user, String tokenHash, Instant expiresAt, String ipAddress, String userAgent) {
        this(user, tokenHash, Instant.now(), expiresAt, ipAddress, userAgent);
    }

    public RefreshToken(User user, String tokenHash, Instant authTime, Instant expiresAt,
                        String ipAddress, String userAgent) {
        this(user, tokenHash, authTime, expiresAt, ipAddress, userAgent, null);
    }

    public RefreshToken(User user, String tokenHash, Instant authTime, Instant expiresAt,
                        String ipAddress, String userAgent, String deviceInfo) {
        this(user, tokenHash, authTime, expiresAt, ipAddress, userAgent, deviceInfo, null);
    }

    public RefreshToken(User user, String tokenHash, Instant authTime, Instant expiresAt,
                        String ipAddress, String userAgent, String deviceInfo, String familyId) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.authTime = authTime;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.lastUsedAt = this.createdAt;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.deviceInfo = deviceInfo;
        this.familyId = familyId;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getAuthTime() {
        return authTime;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getDeviceInfo() {
        return deviceInfo;
    }

    public String getFamilyId() {
        return familyId;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public String getRiskReasons() {
        return riskReasons;
    }

    public void setRisk(Integer riskScore, String riskLevel, String riskReasons) {
        this.riskScore = riskScore;
        this.riskLevel = riskLevel;
        this.riskReasons = riskReasons;
    }

    public String getLoginCountryCode() {
        return loginCountryCode;
    }

    public String getLoginCountryName() {
        return loginCountryName;
    }

    public String getLoginRegion() {
        return loginRegion;
    }

    public String getLoginCity() {
        return loginCity;
    }

    public Double getLoginLatitude() {
        return loginLatitude;
    }

    public Double getLoginLongitude() {
        return loginLongitude;
    }

    public Long getLoginAsn() {
        return loginAsn;
    }

    public String getLoginAsnOrganization() {
        return loginAsnOrganization;
    }

    public void setLoginLocation(String countryCode, String countryName, String region, String city,
                                 Double latitude, Double longitude, Long asn, String organization) {
        this.loginCountryCode = countryCode;
        this.loginCountryName = countryName;
        this.loginRegion = region;
        this.loginCity = city;
        this.loginLatitude = latitude;
        this.loginLongitude = longitude;
        this.loginAsn = asn;
        this.loginAsnOrganization = organization;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
