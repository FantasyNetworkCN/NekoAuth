package neko.fantasy.auth.oauth;

import neko.fantasy.auth.config.OAuthProperties;
import neko.fantasy.auth.domain.User;
import neko.fantasy.auth.exception.ApiException;
import neko.fantasy.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class OAuthAuthorizationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OAuthClientRepository clientRepository;
    private final OAuthConsentRepository consentRepository;
    private final OAuthAuthorizationCodeRepository codeRepository;
    private final UserRepository userRepository;
    private final OAuthProperties properties;
    private final OAuthTokenService tokenService;

    public OAuthAuthorizationService(OAuthClientRepository clientRepository,
                                     OAuthConsentRepository consentRepository,
                                     OAuthAuthorizationCodeRepository codeRepository,
                                     UserRepository userRepository,
                                     OAuthProperties properties,
                                     OAuthTokenService tokenService) {
        this.clientRepository = clientRepository;
        this.consentRepository = consentRepository;
        this.codeRepository = codeRepository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.tokenService = tokenService;
    }

    @Transactional
    public OAuthDtos.AuthorizeView inspect(Long userId, OAuthDtos.AuthorizationRequest request) {
        ValidatedAuthorization validated = validate(request);
        OAuthConsent consent = consentRepository
                .findByUserIdAndClientId(userId, validated.client().getId())
                .orElse(null);
        boolean previouslyApproved = consent != null && grantedScopes(consent).containsAll(validated.scopes());
        boolean forceConsent = hasPrompt(request.prompt(), "consent");
        boolean none = hasPrompt(request.prompt(), "none");

        if (none) {
            if (!previouslyApproved) {
                return view(userId, validated, false, errorRedirect(validated, "login_required",
                        "当前没有可用于完成授权的登录同意记录"));
            }
            return view(userId, validated, true, successRedirect(validated, userId));
        }
        if (previouslyApproved && !forceConsent) {
            return view(userId, validated, true, successRedirect(validated, userId));
        }
        return view(userId, validated, previouslyApproved, null);
    }

    @Transactional
    public String decide(Long userId, OAuthDtos.AuthorizationRequest request, boolean approved) {
        ValidatedAuthorization validated = validate(request);
        if (!approved) {
            return errorRedirect(validated, "access_denied", "用户拒绝了本次授权申请");
        }
        OAuthConsent consent = consentRepository
                .findByUserIdAndClientId(userId, validated.client().getId())
                .orElseGet(() -> new OAuthConsent(
                        requireUser(userId), validated.client(), OAuthScopes.encode(validated.scopes())));
        LinkedHashSet<String> granted = new LinkedHashSet<>(grantedScopes(consent));
        granted.addAll(validated.scopes());
        consent.setScopes(OAuthScopes.encode(granted));
        consentRepository.save(consent);
        return successRedirect(validated, userId);
    }

    private OAuthDtos.AuthorizeView view(Long userId, ValidatedAuthorization validated,
                                         boolean previouslyApproved, String redirectUrl) {
        User user = requireUser(userId);
        return new OAuthDtos.AuthorizeView(
                validated.client().getClientId(),
                validated.client().getName(),
                validated.client().getDescription(),
                safeHost(validated.redirectUri()),
                user.getUsername(),
                user.getDisplayName(),
                validated.scopes(),
                OAuthScopes.describe(validated.scopes()),
                previouslyApproved,
                redirectUrl);
    }

    private String successRedirect(ValidatedAuthorization validated, Long userId) {
        String code = randomToken();
        OAuthAuthorizationCode authorizationCode = new OAuthAuthorizationCode(
                sha256(code),
                validated.client(),
                requireUser(userId),
                OAuthScopes.encode(validated.scopes()),
                validated.redirectUri(),
                validated.codeChallenge(),
                validated.codeChallengeMethod(),
                validated.nonce(),
                Instant.now().plus(properties.authorizationCodeTtl()));
        codeRepository.save(authorizationCode);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("code", code);
        if (validated.state() != null) {
            params.put("state", validated.state());
        }
        params.put("iss", tokenService.issuer());
        return redirect(validated.redirectUri(), params);
    }

    private String errorRedirect(ValidatedAuthorization validated, String error, String description) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("error", error);
        params.put("error_description", description);
        if (validated.state() != null) {
            params.put("state", validated.state());
        }
        params.put("iss", tokenService.issuer());
        return redirect(validated.redirectUri(), params);
    }

    private ValidatedAuthorization validate(OAuthDtos.AuthorizationRequest request) {
        if (request.clientId() == null || request.clientId().isBlank()) {
            throw OAuthException.invalidRequest("缺少 client_id");
        }
        if (request.redirectUri() == null || request.redirectUri().isBlank()) {
            throw OAuthException.invalidRequest("缺少 redirect_uri");
        }
        OAuthClient client = clientRepository.findByClientId(request.clientId())
                .filter(OAuthClient::isEnabled)
                .orElseThrow(() -> OAuthException.invalidRequest("OAuth 应用不存在或已停用"));
        if (!client.hasRedirectUri(request.redirectUri())) {
            throw OAuthException.invalidRequest("redirect_uri 未在应用中登记");
        }
        if (!"code".equals(request.responseType())) {
            throw new OAuthException("unsupported_response_type", "仅支持 authorization code 流程",
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        if (request.state() != null && request.state().length() > 1024) {
            throw OAuthException.invalidRequest("state 不能超过 1024 个字符");
        }
        if (request.nonce() != null && request.nonce().length() > 255) {
            throw OAuthException.invalidRequest("nonce 不能超过 255 个字符");
        }
        LinkedHashSet<String> scopes = OAuthScopes.parseRequested(request.scope());
        if (!client.getScopes().containsAll(scopes)) {
            throw OAuthException.invalidScope("应用未获准申请这些权限范围");
        }

        String challenge = trimToNull(request.codeChallenge());
        String method = trimToNull(request.codeChallengeMethod());
        if (client.isPublicClient() && challenge == null) {
            throw OAuthException.invalidRequest("公共客户端必须使用 PKCE");
        }
        if (challenge != null) {
            if (!"S256".equals(method)) {
                throw OAuthException.invalidRequest("PKCE 仅支持 S256");
            }
            if (!challenge.matches("^[A-Za-z0-9_-]{43,128}$")) {
                throw OAuthException.invalidRequest("code_challenge 格式不正确");
            }
        } else {
            method = null;
        }
        return new ValidatedAuthorization(client, request.redirectUri(), scopes, request.state(),
                trimToNull(request.nonce()), challenge, method);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .filter(User::isEnabled)
                .orElseThrow(() -> ApiException.unauthorized("USER_NOT_FOUND", "用户不存在或已停用"));
    }

    private static Set<String> grantedScopes(OAuthConsent consent) {
        String scopes = consent.getScopes();
        if (scopes == null || scopes.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(Arrays.asList(scopes.trim().split("\\s+")));
    }

    private static boolean hasPrompt(String prompt, String expected) {
        return prompt != null && Arrays.stream(prompt.trim().split("\\s+"))
                .anyMatch(value -> value.equalsIgnoreCase(expected));
    }

    private static String redirect(String uri, Map<String, String> parameters) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(URI.create(uri));
        parameters.forEach((name, value) -> {
            if (value != null && !value.isEmpty()) {
                builder.queryParam(name, value);
            }
        });
        return builder.build().encode().toUriString();
    }

    private static String safeHost(String uri) {
        try {
            URI parsed = URI.create(uri);
            String host = parsed.getHost() == null ? "" : parsed.getHost().toLowerCase(Locale.ROOT);
            int port = parsed.getPort();
            return port < 0 ? host : host + ":" + port;
        } catch (IllegalArgumentException exception) {
            return "unknown";
        }
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ValidatedAuthorization(OAuthClient client, String redirectUri, Set<String> scopes,
                                          String state, String nonce, String codeChallenge,
                                          String codeChallengeMethod) {
    }
}
