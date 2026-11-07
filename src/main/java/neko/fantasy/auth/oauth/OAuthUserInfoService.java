package neko.fantasy.auth.oauth;

import io.jsonwebtoken.JwtException;
import neko.fantasy.auth.domain.User;
import neko.fantasy.auth.repository.RefreshTokenRepository;
import neko.fantasy.auth.repository.UserRepository;
import neko.fantasy.auth.service.AvatarService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class OAuthUserInfoService {

    private final OAuthTokenService tokenService;
    private final OAuthClientRepository clientRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository browserRefreshTokenRepository;
    private final OAuthRevokedAccessTokenRepository revokedAccessTokenRepository;

    public OAuthUserInfoService(OAuthTokenService tokenService, OAuthClientRepository clientRepository,
                                UserRepository userRepository,
                                RefreshTokenRepository browserRefreshTokenRepository,
                                OAuthRevokedAccessTokenRepository revokedAccessTokenRepository) {
        this.tokenService = tokenService;
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.browserRefreshTokenRepository = browserRefreshTokenRepository;
        this.revokedAccessTokenRepository = revokedAccessTokenRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> userInfo(String authorizationHeader) {
        if (authorizationHeader == null
                || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new OAuthException("invalid_token", "缺少访问令牌", HttpStatus.UNAUTHORIZED);
        }
        String token = authorizationHeader.substring(7).trim();
        OAuthTokenService.AccessTokenClaims claims;
        try {
            claims = tokenService.parseAccessToken(token);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new OAuthException("invalid_token", "访问令牌无效或已过期", HttpStatus.UNAUTHORIZED);
        }
        if (revokedAccessTokenRepository.existsById(claims.jti())) {
            throw new OAuthException("invalid_token", "访问令牌已撤销", HttpStatus.UNAUTHORIZED);
        }
        if (!claims.standalone()
                && (claims.sessionFamilyId() == null || claims.sessionFamilyId().isBlank()
                || !browserRefreshTokenRepository
                .existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(
                        claims.sessionFamilyId(), Instant.now()))) {
            throw new OAuthException("invalid_token", "登录会话已失效", HttpStatus.UNAUTHORIZED);
        }
        OAuthClient client = clientRepository.findByClientId(claims.clientId())
                .filter(OAuthClient::isEnabled)
                .orElseThrow(() -> new OAuthException(
                        "invalid_token", "签发访问令牌的应用已停用", HttpStatus.UNAUTHORIZED));
        User user = userRepository.findById(claims.userId())
                .filter(User::isEnabled)
                .filter(candidate -> candidate.getTokenVersion() == claims.tokenVersion())
                .orElseThrow(() -> new OAuthException(
                        "invalid_token", "用户会话已失效", HttpStatus.UNAUTHORIZED));

        Set<String> scopes = Set.copyOf(Arrays.asList(claims.scope().trim().split("\\s+")));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sub", user.getId().toString());
        if (scopes.contains(OAuthScopes.PROFILE)) {
            response.put("preferred_username", user.getUsername());
            response.put("name", user.publicName());
            response.put("picture", tokenService.issuer() + AvatarService.avatarUrl(user.getId()));
            response.put("updated_at", user.getUpdatedAt().getEpochSecond());
        }
        if (scopes.contains(OAuthScopes.EMAIL)) {
            response.put("email", user.getEmail());
        }
        if (scopes.contains(OAuthScopes.ROLES)) {
            response.put("role", user.getRole().name());
        }
        response.put("client_id", client.getClientId());
        return response;
    }
}
