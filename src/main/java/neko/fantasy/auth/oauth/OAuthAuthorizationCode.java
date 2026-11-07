package neko.fantasy.auth.oauth;

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
import neko.fantasy.auth.domain.User;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Entity
@Table(name = "oauth_authorization_codes", indexes = {
        @Index(name = "idx_oauth_codes_auth_session", columnList = "auth_session_family_id"),
        @Index(name = "idx_oauth_codes_expires", columnList = "expires_at")
})
public class OAuthAuthorizationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code_hash", nullable = false, unique = true, length = 64)
    private String codeHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private OAuthClient client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(nullable = false, length = 512)
    private String scopes;

    @Column(name = "redirect_uri", nullable = false, length = 2000)
    private String redirectUri;

    @Column(name = "code_challenge", length = 128)
    private String codeChallenge;

    @Column(name = "code_challenge_method", length = 16)
    private String codeChallengeMethod;

    @Column(length = 255)
    private String nonce;

    @Column(name = "auth_time")
    private Instant authTime;

    /** 发起本次授权的 Auth 浏览器会话族；OAuth 令牌不得脱离该会话继续使用。 */
    @Column(name = "auth_session_family_id", length = 64)
    private String authSessionFamilyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected OAuthAuthorizationCode() {
    }

    public OAuthAuthorizationCode(String codeHash, OAuthClient client, User user, String scopes,
                                  String redirectUri, String codeChallenge, String codeChallengeMethod,
                                  String nonce, Instant expiresAt) {
        this(codeHash, client, user, scopes, redirectUri, codeChallenge, codeChallengeMethod,
                nonce, null, expiresAt);
    }

    public OAuthAuthorizationCode(String codeHash, OAuthClient client, User user, String scopes,
                                  String redirectUri, String codeChallenge, String codeChallengeMethod,
                                  String nonce, Instant authTime, Instant expiresAt) {
        this(codeHash, client, user, scopes, redirectUri, codeChallenge, codeChallengeMethod,
                nonce, authTime, expiresAt, null);
    }

    public OAuthAuthorizationCode(String codeHash, OAuthClient client, User user, String scopes,
                                  String redirectUri, String codeChallenge, String codeChallengeMethod,
                                  String nonce, Instant authTime, Instant expiresAt,
                                  String authSessionFamilyId) {
        this.codeHash = codeHash;
        this.client = client;
        this.user = user;
        this.scopes = scopes;
        this.redirectUri = redirectUri;
        this.codeChallenge = codeChallenge;
        this.codeChallengeMethod = codeChallengeMethod;
        this.nonce = nonce;
        this.authTime = authTime;
        this.authSessionFamilyId = authSessionFamilyId;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public OAuthClient getClient() {
        return client;
    }

    public User getUser() {
        return user;
    }

    public String getScopes() {
        return scopes;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public String getCodeChallenge() {
        return codeChallenge;
    }

    public String getCodeChallengeMethod() {
        return codeChallengeMethod;
    }

    public String getNonce() {
        return nonce;
    }

    public Instant getAuthTime() {
        return authTime;
    }

    public String getAuthSessionFamilyId() {
        return authSessionFamilyId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void consume() {
        this.consumedAt = Instant.now();
    }

    public boolean isUsable(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }
}
