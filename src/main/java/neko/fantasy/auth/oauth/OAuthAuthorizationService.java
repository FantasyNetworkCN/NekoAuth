package neko.fantasy.auth.oauth;

import neko.fantasy.auth.config.AuthProperties;
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
import java.time.Duration;
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
    private static final Set<String> SUPPORTED_PROMPTS = Set.of(
            "none", "login", "consent", "select_account");

    private final OAuthClientRepository clientRepository;
    private final OAuthConsentRepository consentRepository;
    private final OAuthAuthorizationCodeRepository codeRepository;
    private final UserRepository userRepository;
    private final OAuthProperties properties;
    private final AuthProperties authProperties;
    private final OAuthTokenService tokenService;

    public OAuthAuthorizationService(OAuthClientRepository clientRepository,
                                     OAuthConsentRepository consentRepository,
                                     OAuthAuthorizationCodeRepository codeRepository,
                                     UserRepository userRepository,
                                     OAuthProperties properties,
                                     AuthProperties authProperties,
                                     OAuthTokenService tokenService) {
        this.clientRepository = clientRepository;
        this.consentRepository = consentRepository;
        this.codeRepository = codeRepository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.authProperties = authProperties;
        this.tokenService = tokenService;
    }

    @Transactional(readOnly = true)
    public OAuthDtos.AuthorizeView inspectAnonymous(OAuthDtos.AuthorizationRequest request) {
        if (!parsePrompts(request.prompt()).contains("none")) {
            throw ApiException.unauthorized("LOGIN_REQUIRED", "需要登录后才能继续授权");
        }
        ValidatedAuthorization validated = validate(request);
        return new OAuthDtos.AuthorizeView(
                validated.client().getClientId(),
                validated.client().getName(),
                validated.client().getDescription(),
                safeHost(validated.redirectUri()),
                null,
                null,
                validated.scopes(),
                OAuthScopes.describe(validated.scopes()),
                false,
                errorRedirect(validated, "login_required", "需要登录后才能完成本次授权"),
                false,
                validated.loginHint());
    }

    @Transactional
    public OAuthDtos.AuthorizeView inspect(Long userId, Instant authenticatedAt,
                                           String sessionFamilyId,
                                           OAuthDtos.AuthorizationRequest request) {
        ValidatedAuthorization validated = validate(request);
        User user = requireUser(userId);
        String loginHint = loginHint(validated, user);
        boolean reauthRequired = requiresReauthentication(validated, authenticatedAt);
        OAuthConsent consent = consentRepository
                .findByUserIdAndClientId(userId, validated.client().getId())
                .orElse(null);
        boolean previouslyApproved = consent != null && grantedScopes(consent).containsAll(validated.scopes());

        if (validated.prompts().contains("none")) {
            if (reauthRequired || !matchesLoginHint(validated.loginHint(), user)) {
                return view(user, validated, previouslyApproved,
                        errorRedirect(validated, "login_required", "需要重新登录后才能完成本次授权"),
                        false, loginHint);
            }
            if (!previouslyApproved) {
                if (!isFirstPartyRedirect(validated.redirectUri())) {
                    return view(user, validated, false,
                            errorRedirect(validated, "consent_required", "当前没有可用于完成授权的登录同意记录"),
                            false, loginHint);
                }
                saveConsent(user, validated.client(), validated.scopes(), consent);
                previouslyApproved = true;
            }
            return view(user, validated, true,
                    successRedirect(validated, user, authenticatedAt, sessionFamilyId),
                    false, loginHint);
        }

        if (reauthRequired) {
            return view(user, validated, previouslyApproved, null, true, loginHint);
        }

        boolean forceConsent = validated.prompts().contains("consent");
        if (previouslyApproved && !forceConsent) {
            return view(user, validated, true,
                    successRedirect(validated, user, authenticatedAt, sessionFamilyId),
                    false, loginHint);
        }
        return view(user, validated, previouslyApproved, null, false, loginHint);
    }

    @Transactional
    public String decide(Long userId, Instant authenticatedAt,
                         String sessionFamilyId,
                         OAuthDtos.AuthorizationRequest request, boolean approved) {
        ValidatedAuthorization validated = validate(request);
        User user = requireUser(userId);
        if (requiresReauthentication(validated, authenticatedAt)) {
            return errorRedirect(validated, "login_required", "需要重新登录后才能完成本次授权");
        }
        if (!approved) {
            return errorRedirect(validated, "access_denied", "用户拒绝了本次授权申请");
        }
        OAuthConsent consent = consentRepository
                .findByUserIdAndClientId(userId, validated.client().getId())
                .orElse(null);
        saveConsent(user, validated.client(), validated.scopes(), consent);
        return successRedirect(validated, user, authenticatedAt, sessionFamilyId);
    }

    private void saveConsent(User user, OAuthClient client, Set<String> requestedScopes,
                             OAuthConsent existing) {
        OAuthConsent consent = existing == null
                ? new OAuthConsent(user, client, "")
                : existing;
        LinkedHashSet<String> granted = new LinkedHashSet<>(grantedScopes(consent));
        granted.addAll(requestedScopes);
        consent.setScopes(OAuthScopes.encode(granted));
        consentRepository.save(consent);
    }

    /**
     * 同一父域下的已登记应用视为第一方 Web。只有 {@code prompt=none} 才会静默补建同意记录，
     * 正常交互式授权仍会展示同意页。
     */
    private boolean isFirstPartyRedirect(String redirectUri) {
        String configuredDomain = authProperties == null || authProperties.cookie() == null
                ? "" : authProperties.cookie().domain();
        String domain = configuredDomain == null ? "" : configuredDomain.trim().toLowerCase(Locale.ROOT);
        while (domain.startsWith(".")) {
            domain = domain.substring(1);
        }
        if (domain.isEmpty()) {
            return false;
        }
        try {
            URI redirect = URI.create(redirectUri);
            String scheme = redirect.getScheme() == null
                    ? "" : redirect.getScheme().toLowerCase(Locale.ROOT);
            String host = redirect.getHost() == null
                    ? "" : redirect.getHost().toLowerCase(Locale.ROOT);
            return "https".equals(scheme)
                    && (host.equals(domain) || host.endsWith("." + domain));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private OAuthDtos.AuthorizeView view(User user, ValidatedAuthorization validated,
                                         boolean previouslyApproved, String redirectUrl,
                                         boolean reauthRequired, String loginHint) {
        return new OAuthDtos.AuthorizeView(
                validated.client().getClientId(),
                validated.client().getName(),
                validated.client().getDescription(),
                safeHost(validated.redirectUri()),
                user.getUsername(),
                user.publicName(),
                validated.scopes(),
                OAuthScopes.describe(validated.scopes()),
                previouslyApproved,
                redirectUrl,
                reauthRequired,
                loginHint);
    }

    private String successRedirect(ValidatedAuthorization validated, User user, Instant authenticatedAt,
                                   String sessionFamilyId) {
        String code = randomToken();
        OAuthAuthorizationCode authorizationCode = new OAuthAuthorizationCode(
                sha256(code),
                validated.client(),
                user,
                OAuthScopes.encode(validated.scopes()),
                validated.redirectUri(),
                validated.codeChallenge(),
                validated.codeChallengeMethod(),
                validated.nonce(),
                authenticatedAt,
                Instant.now().plus(properties.authorizationCodeTtl()),
                sessionFamilyId);
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
        String loginHint = trimToNull(request.loginHint());
        if (loginHint != null && loginHint.length() > 255) {
            throw OAuthException.invalidRequest("login_hint 不能超过 255 个字符");
        }
        Set<String> prompts = parsePrompts(request.prompt());
        Long maxAge = parseMaxAge(request.maxAge());
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
                trimToNull(request.nonce()), challenge, method, prompts, loginHint, maxAge);
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

    private static Set<String> parsePrompts(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return Set.of();
        }
        LinkedHashSet<String> prompts = new LinkedHashSet<>();
        for (String value : prompt.trim().split("\\s+")) {
            if (!SUPPORTED_PROMPTS.contains(value)) {
                throw OAuthException.invalidRequest("不支持的 prompt：" + value);
            }
            prompts.add(value);
        }
        if (prompts.contains("none") && prompts.size() > 1) {
            throw OAuthException.invalidRequest("prompt=none 不能与其他 prompt 值同时使用");
        }
        return Set.copyOf(prompts);
    }

    private static Long parseMaxAge(String value) {
        String maxAge = trimToNull(value);
        if (maxAge == null) {
            return null;
        }
        if (!maxAge.matches("\\d{1,19}")) {
            throw OAuthException.invalidRequest("max_age 必须是非负整数");
        }
        try {
            return Long.parseLong(maxAge);
        } catch (NumberFormatException exception) {
            throw OAuthException.invalidRequest("max_age 超出有效范围");
        }
    }

    private static boolean requiresReauthentication(ValidatedAuthorization validated,
                                                     Instant authenticatedAt) {
        if (validated.prompts().contains("login") || validated.prompts().contains("select_account")) {
            return true;
        }
        if (validated.maxAge() == null) {
            return false;
        }
        if (validated.maxAge() == 0 || authenticatedAt == null) {
            return true;
        }
        long age = Duration.between(authenticatedAt, Instant.now()).getSeconds();
        return age >= validated.maxAge();
    }

    private static String loginHint(ValidatedAuthorization validated, User user) {
        return validated.loginHint() == null ? user.getUsername() : validated.loginHint();
    }

    private static boolean matchesLoginHint(String loginHint, User user) {
        return loginHint == null
                || loginHint.equalsIgnoreCase(user.getUsername())
                || loginHint.equalsIgnoreCase(user.getEmail());
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
            String host = parsed.getHost();
            if (host == null || host.isBlank()) {
                return parsed.getScheme() == null ? "unknown" : parsed.getScheme().toLowerCase(Locale.ROOT);
            }
            host = host.toLowerCase(Locale.ROOT);
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
                                          String codeChallengeMethod, Set<String> prompts,
                                          String loginHint, Long maxAge) {
    }
}
