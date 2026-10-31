package neko.fantasy.auth.oauth;

import io.jsonwebtoken.JwtException;
import neko.fantasy.auth.service.AuthService;
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
    private final AuthService authService;

    public OAuthLogoutService(OAuthClientRepository clientRepository,
                              OAuthRefreshTokenRepository refreshTokenRepository,
                              OAuthTokenService tokenService,
                              AuthService authService) {
        this.clientRepository = clientRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenService = tokenService;
        this.authService = authService;
    }

    /**
     * 校验 RP-Initiated Logout 参数，并判断是否必须由当前浏览器用户确认。
     * 只有 {@code id_token_hint} 对应用户与当前浏览器登录会话一致时才允许直接登出。
     */
    @Transactional(readOnly = true)
    public LogoutPlan plan(LogoutParameters parameters, String rawRefreshToken) {
        if (parameters.state() != null && parameters.state().length() > 1024) {
            throw OAuthException.invalidRequest("state 不能超过 1024 个字符");
        }

        OAuthClient client = null;
        OAuthTokenService.IdTokenHint hint = null;
        if (parameters.idTokenHint() != null && !parameters.idTokenHint().isBlank()) {
            try {
                hint = tokenService.parseIdTokenHint(parameters.idTokenHint().trim());
            } catch (JwtException | IllegalArgumentException exception) {
                throw OAuthException.invalidRequest("id_token_hint 无效");
            }
            if (parameters.clientId() != null && !parameters.clientId().isBlank()
                    && !parameters.clientId().equals(hint.clientId())) {
                throw OAuthException.invalidRequest("id_token_hint 与 client_id 不匹配");
            }
            client = clientRepository.findByClientId(hint.clientId()).orElse(null);
            if (client == null) {
                throw OAuthException.invalidRequest("id_token_hint 对应的应用不存在");
            }
        } else if (parameters.clientId() != null && !parameters.clientId().isBlank()) {
            client = clientRepository.findByClientId(parameters.clientId())
                    .orElseThrow(() -> OAuthException.invalidRequest("client_id 对应的应用不存在"));
        }

        if (parameters.postLogoutRedirectUri() != null
                && !parameters.postLogoutRedirectUri().isBlank()) {
            if (client == null) {
                throw new OAuthException("invalid_request",
                        "使用 post_logout_redirect_uri 时必须提供 id_token_hint 或 client_id",
                        HttpStatus.BAD_REQUEST);
            }
            if (!client.hasPostLogoutRedirectUri(parameters.postLogoutRedirectUri())) {
                throw OAuthException.invalidRequest("post_logout_redirect_uri 未在应用中登记");
            }
        }

        AuthService.ActiveSession session = authService.findActiveSession(rawRefreshToken).orElse(null);
        boolean sessionMatchesHint = hint != null && session != null
                && session.userId().equals(hint.userId());

        Long revokeUserId = null;
        Long revokeClientId = null;
        ConfirmationReason confirmationReason = null;
        if (hint == null) {
            confirmationReason = ConfirmationReason.MISSING_HINT;
            if (client != null && session != null) {
                revokeUserId = session.userId();
                revokeClientId = client.getId();
            }
        } else if (session == null) {
            confirmationReason = ConfirmationReason.NO_SESSION;
        } else if (!sessionMatchesHint) {
            confirmationReason = ConfirmationReason.SESSION_MISMATCH;
        } else {
            revokeUserId = hint.userId();
            revokeClientId = client.getId();
        }

        String target = parameters.postLogoutRedirectUri() == null
                || parameters.postLogoutRedirectUri().isBlank()
                ? tokenService.issuer() + "/login?reason=logged-out"
                : appendState(parameters.postLogoutRedirectUri(), parameters.state());
        return new LogoutPlan(confirmationReason != null, confirmationReason, target,
                revokeUserId, revokeClientId);
    }

    /** 执行已经由用户确认或与当前会话匹配的登出，并撤销对应应用的活动刷新令牌。 */
    @Transactional
    public String complete(LogoutParameters parameters, String rawRefreshToken) {
        LogoutPlan plan = plan(parameters, rawRefreshToken);
        if (plan.revokeUserId() != null && plan.revokeClientId() != null) {
            refreshTokenRepository.revokeActiveForFamily(
                    plan.revokeUserId(), plan.revokeClientId(), Instant.now());
        }
        authService.logout(rawRefreshToken);
        return plan.target();
    }

    private static String appendState(String uri, String state) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(URI.create(uri));
        if (state != null && !state.isEmpty()) {
            builder.queryParam("state", state);
        }
        return builder.build().encode().toUriString();
    }

    public record LogoutParameters(String idTokenHint, String clientId,
                                   String postLogoutRedirectUri, String state) {
    }

    public record LogoutPlan(boolean confirmationRequired, ConfirmationReason confirmationReason,
                             String target, Long revokeUserId, Long revokeClientId) {
    }

    public enum ConfirmationReason {
        MISSING_HINT("missing_hint"),
        NO_SESSION("no_session"),
        SESSION_MISMATCH("session_mismatch");

        private final String code;

        ConfirmationReason(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
