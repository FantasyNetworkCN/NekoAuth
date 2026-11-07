package neko.fantasy.auth.oauth;

import io.jsonwebtoken.JwtException;
import neko.fantasy.auth.config.OAuthProperties;
import neko.fantasy.auth.domain.User;
import neko.fantasy.auth.repository.RefreshTokenRepository;
import neko.fantasy.auth.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class OAuthTokenEndpointService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OAuthClientRepository clientRepository;
    private final OAuthAuthorizationCodeRepository codeRepository;
    private final OAuthRefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenRepository browserRefreshTokenRepository;
    private final OAuthRevokedAccessTokenRepository revokedAccessTokenRepository;
    private final UserRepository userRepository;
    private final OAuthTokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final OAuthProperties properties;
    private final OAuthRefreshTokenReuseService reuseService;

    public OAuthTokenEndpointService(OAuthClientRepository clientRepository,
                                     OAuthAuthorizationCodeRepository codeRepository,
                                     OAuthRefreshTokenRepository refreshTokenRepository,
                                     RefreshTokenRepository browserRefreshTokenRepository,
                                     OAuthRevokedAccessTokenRepository revokedAccessTokenRepository,
                                     UserRepository userRepository,
                                     OAuthTokenService tokenService,
                                     PasswordEncoder passwordEncoder,
                                     OAuthProperties properties,
                                     OAuthRefreshTokenReuseService reuseService) {
        this.clientRepository = clientRepository;
        this.codeRepository = codeRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.browserRefreshTokenRepository = browserRefreshTokenRepository;
        this.revokedAccessTokenRepository = revokedAccessTokenRepository;
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.reuseService = reuseService;
    }

    @Transactional
    public Map<String, Object> token(Map<String, String> form, String authorizationHeader) {
        OAuthClient client = authenticateClient(form, authorizationHeader);
        String grantType = form.get("grant_type");
        if (grantType == null || grantType.isBlank()) {
            throw OAuthException.invalidRequest("缺少参数：grant_type");
        }
        if ("authorization_code".equals(grantType)) {
            return authorizationCodeGrant(client, form);
        }
        if ("refresh_token".equals(grantType)) {
            return refreshTokenGrant(client, form);
        }
        throw OAuthException.unsupportedGrantType("仅支持 authorization_code 与 refresh_token");
    }

    @Transactional
    public void revoke(Map<String, String> form, String authorizationHeader) {
        OAuthClient client = authenticateClient(form, authorizationHeader);
        String token = form.get("token");
        if (token == null || token.isBlank()) {
            throw OAuthException.invalidRequest("缺少 token");
        }
        OAuthRefreshToken refreshToken = refreshTokenRepository.findByTokenHash(sha256(token))
                .filter(stored -> stored.getClient().getId().equals(client.getId()))
                .filter(stored -> stored.isActive(Instant.now()))
                .orElse(null);
        if (refreshToken != null) {
            refreshToken.revoke();
            return;
        }

        try {
            OAuthTokenService.AccessTokenClaims claims = tokenService.parseAccessToken(token);
            if (client.getClientId().equals(claims.clientId())) {
                revokedAccessTokenRepository.save(new OAuthRevokedAccessToken(
                        claims.jti(), claims.expiresAt()));
            }
        } catch (JwtException | IllegalArgumentException ignored) {
            // RFC 7009：无效或已过期的令牌同样返回成功，避免泄露令牌状态。
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> introspect(Map<String, String> form, String authorizationHeader) {
        OAuthClient client = authenticateClient(form, authorizationHeader);
        String token = form.get("token");
        if (token == null || token.isBlank()) {
            throw OAuthException.invalidRequest("缺少 token");
        }

        OAuthRefreshToken refreshToken = refreshTokenRepository.findByTokenHash(sha256(token)).orElse(null);
        if (refreshToken != null) {
            if (!refreshToken.getClient().getId().equals(client.getId())
                    || !refreshToken.isActive(Instant.now())
                    || !refreshToken.getUser().isEnabled()) {
                return Map.of("active", false);
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("active", true);
            response.put("client_id", client.getClientId());
            response.put("token_type", "refresh_token");
            response.put("scope", refreshToken.getScopes());
            response.put("sub", refreshToken.getUser().getId().toString());
            response.put("username", refreshToken.getUser().getUsername());
            response.put("name", refreshToken.getUser().publicName());
            response.put("email", refreshToken.getUser().getEmail());
            response.put("iss", tokenService.issuer());
            return response;
        }

        try {
            OAuthTokenService.AccessTokenClaims claims = tokenService.parseAccessToken(token);
            if (!client.getClientId().equals(claims.clientId())) {
                return Map.of("active", false);
            }
            if (revokedAccessTokenRepository.existsById(claims.jti())) {
                return Map.of("active", false);
            }
            if (claims.sessionFamilyId() != null && !claims.sessionFamilyId().isBlank()
                    && browserRefreshTokenRepository
                    .findFirstByFamilyIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                            claims.sessionFamilyId(), Instant.now())
                    .isEmpty()) {
                return Map.of("active", false);
            }
            User user = userRepository.findById(claims.userId())
                    .filter(User::isEnabled)
                    .filter(candidate -> candidate.getTokenVersion() == claims.tokenVersion())
                    .orElse(null);
            if (user == null) {
                return Map.of("active", false);
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("active", true);
            response.put("client_id", claims.clientId());
            response.put("token_type", "Bearer");
            response.put("scope", claims.scope());
            response.put("sub", claims.userId().toString());
            if (claims.username() != null) {
                response.put("username", claims.username());
            }
            response.put("name", user.publicName());
            response.put("picture", tokenService.issuer()
                    + neko.fantasy.auth.service.AvatarService.avatarUrl(user.getId()));
            response.put("email", user.getEmail());
            if (claims.role() != null) {
                response.put("role", claims.role());
            }
            response.put("exp", claims.expiresAt().getEpochSecond());
            response.put("iat", claims.issuedAt().getEpochSecond());
            response.put("iss", tokenService.issuer());
            response.put("jti", claims.jti());
            return response;
        } catch (JwtException | IllegalArgumentException exception) {
            return Map.of("active", false);
        }
    }

    @Scheduled(cron = "0 37 4 * * *")
    @Transactional
    public void purgeExpiredAccessTokenRevocations() {
        revokedAccessTokenRepository.deleteExpired(Instant.now());
    }

    private Map<String, Object> authorizationCodeGrant(OAuthClient client, Map<String, String> form) {
        String code = required(form, "code");
        String redirectUri = required(form, "redirect_uri");
        OAuthAuthorizationCode authorizationCode = codeRepository.findByHashForUpdate(sha256(code))
                .orElseThrow(() -> OAuthException.invalidGrant("授权码无效"));
        Instant now = Instant.now();
        if (!authorizationCode.isUsable(now)) {
            throw OAuthException.invalidGrant("授权码已过期或已被使用");
        }
        if (!authorizationCode.getClient().getId().equals(client.getId())) {
            throw OAuthException.invalidGrant("授权码不属于当前客户端");
        }
        if (!authorizationCode.getRedirectUri().equals(redirectUri)) {
            throw OAuthException.invalidGrant("redirect_uri 与授权请求不一致");
        }
        verifyPkce(authorizationCode, form.get("code_verifier"));
        authorizationCode.consume();
        return issueTokens(client, authorizationCode.getUser(),
                splitScopes(authorizationCode.getScopes()), authorizationCode.getNonce(),
                authorizationCode.getAuthTime(), true);
    }

    private Map<String, Object> refreshTokenGrant(OAuthClient client, Map<String, String> form) {
        String rawToken = required(form, "refresh_token");
        String tokenHash = sha256(rawToken);
        OAuthRefreshToken observed = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> OAuthException.invalidGrant("刷新令牌无效"));
        if (!observed.getClient().getId().equals(client.getId())) {
            throw OAuthException.invalidGrant("刷新令牌不属于当前客户端");
        }
        if (!observed.isActive(Instant.now())) {
            if (observed.wasRotated()) {
                String familyId = observed.getFamilyId() == null
                        ? observed.getTokenHash() : observed.getFamilyId();
                reuseService.revokeFamily(familyId);
            }
            throw OAuthException.invalidGrant("刷新令牌已过期或已被撤销");
        }
        OAuthRefreshToken refreshToken = refreshTokenRepository.findByHashForUpdate(tokenHash)
                .orElseThrow(() -> OAuthException.invalidGrant("刷新令牌无效"));
        if (!refreshToken.isActive(Instant.now())) {
            throw OAuthException.invalidGrant("刷新令牌已过期或已被撤销");
        }
        LinkedHashSet<String> originalScopes = splitScopes(refreshToken.getScopes());
        LinkedHashSet<String> scopes = originalScopes;
        String requestedScope = form.get("scope");
        if (requestedScope != null && !requestedScope.isBlank()) {
            scopes = OAuthScopes.parseRequested(requestedScope);
            if (!originalScopes.containsAll(scopes)) {
                throw OAuthException.invalidScope("刷新令牌不能申请新的权限范围");
            }
        }
        refreshToken.markUsed();
        refreshToken.revoke();
        return issueTokens(client, refreshToken.getUser(), scopes, null,
                refreshToken.getAuthTime(), true,
                refreshToken.getFamilyId() == null
                        ? refreshToken.getTokenHash() : refreshToken.getFamilyId());
    }

    private Map<String, Object> issueTokens(OAuthClient client, User user, Set<String> scopes,
                                            String nonce, Instant authenticatedAt,
                                            boolean rotateRefreshToken) {
        return issueTokens(client, user, scopes, nonce, authenticatedAt, rotateRefreshToken, null);
    }

    private Map<String, Object> issueTokens(OAuthClient client, User user, Set<String> scopes,
                                            String nonce, Instant authenticatedAt,
                                            boolean rotateRefreshToken, String refreshFamilyId) {
        if (!user.isEnabled()) {
            throw OAuthException.invalidGrant("用户账户已停用");
        }
        Instant authTime = authenticatedAt != null
                ? authenticatedAt
                : (user.getLastLoginAt() == null ? Instant.now() : user.getLastLoginAt());
        OAuthTokenService.IssuedAccessToken accessToken = tokenService.issueAccessToken(
                user, client.getClientId(), scopes);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("access_token", accessToken.value());
        response.put("token_type", "Bearer");
        response.put("expires_in", Math.max(0,
                Duration.between(Instant.now(), accessToken.expiresAt()).toSeconds()));
        response.put("scope", OAuthScopes.encode(scopes));
        if (scopes.contains(OAuthScopes.OPENID)) {
            response.put("id_token", tokenService.issueIdToken(
                    user, client.getClientId(), scopes, nonce, accessToken.value(), authTime));
        }
        if (scopes.contains(OAuthScopes.OFFLINE_ACCESS) && rotateRefreshToken) {
            String rawRefreshToken = randomToken(32);
            OAuthRefreshToken refreshToken = new OAuthRefreshToken(
                    sha256(rawRefreshToken), client, user, OAuthScopes.encode(scopes), authTime,
                    Instant.now().plus(properties.refreshTokenTtl()),
                    refreshFamilyId == null ? UUID.randomUUID().toString() : refreshFamilyId);
            refreshTokenRepository.save(refreshToken);
            response.put("refresh_token", rawRefreshToken);
        }
        return response;
    }

    private void verifyPkce(OAuthAuthorizationCode code, String verifier) {
        if (code.getCodeChallenge() == null) {
            return;
        }
        if (verifier == null || verifier.isBlank()) {
            throw OAuthException.invalidGrant("缺少 code_verifier");
        }
        if (!verifier.matches("^[A-Za-z0-9._~-]{43,128}$")) {
            throw OAuthException.invalidGrant("code_verifier 格式不正确");
        }
        String challenge = base64UrlSha256(verifier);
        if (!MessageDigest.isEqual(challenge.getBytes(StandardCharsets.US_ASCII),
                code.getCodeChallenge().getBytes(StandardCharsets.US_ASCII))) {
            throw OAuthException.invalidGrant("code_verifier 与授权请求不匹配");
        }
    }

    private OAuthClient authenticateClient(Map<String, String> form, String authorizationHeader) {
        String bodyClientId = trimToNull(form.get("client_id"));
        String bodySecret = trimToNull(form.get("client_secret"));
        String clientId = bodyClientId;
        String secret = bodySecret;
        boolean basic = false;

        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            if (!authorizationHeader.regionMatches(true, 0, "Basic ", 0, 6)) {
                throw OAuthException.invalidClient("仅支持 HTTP Basic 客户端认证");
            }
            if (bodyClientId != null || bodySecret != null) {
                throw OAuthException.invalidRequest("客户端认证方式不能混用");
            }
            basic = true;
            try {
                String decoded = new String(Base64.getDecoder().decode(
                        authorizationHeader.substring(6).trim()), StandardCharsets.UTF_8);
                int separator = decoded.indexOf(':');
                if (separator < 0) {
                    throw new IllegalArgumentException("missing colon");
                }
                // RFC 7617：Basic 的 user-id / password 按原样拼接后再 Base64。客户端 ID
                // 与密钥可能包含 URL 保留字符，不能再做一次 application/x-www-form-urlencoded
                // 解码，否则 `+` 等字符会被改写，导致合法客户端随机认证失败。
                clientId = decoded.substring(0, separator);
                secret = decoded.substring(separator + 1);
            } catch (IllegalArgumentException exception) {
                throw OAuthException.invalidClient("HTTP Basic 客户端认证格式不正确");
            }
        }
        if (clientId == null || clientId.isBlank()) {
            throw OAuthException.invalidClient("缺少 client_id");
        }

        OAuthClient client = clientRepository.findByClientId(clientId)
                .filter(OAuthClient::isEnabled)
                .orElseThrow(() -> OAuthException.invalidClient("客户端不存在或已停用"));
        if (client.isPublicClient()) {
            if (secret != null && !secret.isBlank()) {
                throw OAuthException.invalidClient("公共客户端不能使用客户端密钥");
            }
            if (basic && secret != null && !secret.isEmpty()) {
                throw OAuthException.invalidClient("公共客户端不能使用客户端密钥");
            }
            return client;
        }
        if (secret == null || secret.isBlank()
                || !passwordEncoder.matches(secret, client.getClientSecretHash())) {
            throw OAuthException.invalidClient("客户端密钥不正确");
        }
        return client;
    }

    private static String required(Map<String, String> form, String name) {
        String value = form.get(name);
        if (value == null || value.isBlank()) {
            throw OAuthException.invalidRequest("缺少参数：" + name);
        }
        return value;
    }

    private static LinkedHashSet<String> splitScopes(String value) {
        if (value == null || value.isBlank()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(Arrays.asList(value.trim().split("\\s+")));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String randomToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String base64UrlSha256(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
