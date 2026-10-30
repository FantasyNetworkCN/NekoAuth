package neko.fantasy.auth.oauth;

import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;

@Service
public class OAuthLogoutService {

    private final OAuthClientRepository clientRepository;
    private final OAuthRefreshTokenRepository refreshTokenRepository;
    private final OAuthTokenService tokenService;

    public OAuthLogoutService(OAuthClientRepository clientRepository,
                              OAuthRefreshTokenRepository refreshTokenRepository,
                              OAuthTokenService tokenService) {
        this.clientRepository = clientRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenService = tokenService;
    }

    @Transactional
    public String logout(String idTokenHint, String requestedClientId,
                         String postLogoutRedirectUri, String state) {
        if (state != null && state.length() > 1024) {
            throw OAuthException.invalidRequest("state 不能超过 1024 个字符");
        }

        OAuthClient client = null;
        OAuthTokenService.IdTokenHint hint = null;
        if (idTokenHint != null && !idTokenHint.isBlank()) {
            try {
                hint = tokenService.parseIdTokenHint(idTokenHint.trim());
            } catch (JwtException | IllegalArgumentException exception) {
                throw OAuthException.invalidRequest("id_token_hint 无效");
            }
            if (requestedClientId != null && !requestedClientId.isBlank()
                    && !requestedClientId.equals(hint.clientId())) {
                throw OAuthException.invalidRequest("id_token_hint 与 client_id 不匹配");
            }
            client = clientRepository.findByClientId(hint.clientId()).orElse(null);
            if (client == null) {
                throw OAuthException.invalidRequest("id_token_hint 对应的应用不存在");
            }
        } else if (requestedClientId != null && !requestedClientId.isBlank()) {
            client = clientRepository.findByClientId(requestedClientId)
                    .orElseThrow(() -> OAuthException.invalidRequest("client_id 对应的应用不存在"));
        }

        if (postLogoutRedirectUri != null && !postLogoutRedirectUri.isBlank()) {
            if (client == null) {
                throw new OAuthException("invalid_request",
                        "使用 post_logout_redirect_uri 时必须提供 id_token_hint 或 client_id",
                        HttpStatus.BAD_REQUEST);
            }
            if (!client.hasPostLogoutRedirectUri(postLogoutRedirectUri)) {
                throw OAuthException.invalidRequest("post_logout_redirect_uri 未在应用中登记");
            }
        }

        if (hint != null) {
            refreshTokenRepository.revokeActiveForFamily(hint.userId(), client.getId(), Instant.now());
        }

        String target = postLogoutRedirectUri == null || postLogoutRedirectUri.isBlank()
                ? tokenService.issuer() + "/login?reason=logged-out"
                : appendState(postLogoutRedirectUri, state);
        return target;
    }

    private static String appendState(String uri, String state) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(URI.create(uri));
        if (state != null && !state.isEmpty()) {
            builder.queryParam("state", state);
        }
        return builder.build().encode().toUriString();
    }
}
