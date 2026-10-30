package neko.fantasy.auth.oauth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "oauth_clients")
public class OAuthClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, unique = true, length = 64)
    private String clientId;

    @Column(name = "client_secret_hash", length = 100)
    private String clientSecretHash;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "redirect_uris", nullable = false, length = 4000)
    private String redirectUris;

    @Column(nullable = false, length = 512)
    private String scopes;

    @Column(name = "public_client", nullable = false)
    private boolean publicClient;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OAuthClient() {
    }

    public OAuthClient(String clientId, String clientSecretHash, String name, String description,
                       List<String> redirectUris, List<String> scopes, boolean publicClient) {
        this.clientId = clientId;
        this.clientSecretHash = clientSecretHash;
        this.name = name;
        this.description = description;
        this.publicClient = publicClient;
        setRedirectUris(redirectUris);
        setScopes(scopes);
    }

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

    public String getClientId() {
        return clientId;
    }

    public String getClientSecretHash() {
        return clientSecretHash;
    }

    public void setClientSecretHash(String clientSecretHash) {
        this.clientSecretHash = clientSecretHash;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getRedirectUris() {
        return split(redirectUris);
    }

    public void setRedirectUris(List<String> redirectUris) {
        this.redirectUris = String.join("\n", redirectUris);
    }

    public List<String> getScopes() {
        return split(scopes);
    }

    public void setScopes(List<String> scopes) {
        this.scopes = String.join(" ", scopes);
    }

    public boolean isPublicClient() {
        return publicClient;
    }

    public void setPublicClient(boolean publicClient) {
        this.publicClient = publicClient;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean hasRedirectUri(String redirectUri) {
        return getRedirectUris().contains(redirectUri);
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\s+"))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }
}
