package neko.fantasy.auth.oauth;

import neko.fantasy.auth.exception.ApiException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class OAuthClientAdminService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Set<String> DANGEROUS_SCHEMES = Set.of(
            "javascript", "data", "file", "about", "blob", "vbscript");

    private final OAuthClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    public OAuthClientAdminService(OAuthClientRepository clientRepository, PasswordEncoder passwordEncoder) {
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<OAuthDtos.ClientSummary> list() {
        return clientRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(OAuthDtos.ClientSummary::from)
                .toList();
    }

    @Transactional
    public OAuthDtos.CreatedClient create(OAuthAdminRequests.CreateClient request) {
        String clientId = "fn_" + randomToken(18);
        List<String> redirectUris = validateRedirectUris(request.redirectUris(), request.publicClient());
        List<String> postLogoutRedirectUris = validatePostLogoutRedirectUris(
                request.postLogoutRedirectUris(), request.publicClient());
        LinkedHashSet<String> scopes = validateScopes(request.scopes());
        String secret = request.publicClient() ? null : "fns_" + randomToken(32);
        OAuthClient client = new OAuthClient(
                clientId,
                secret == null ? null : passwordEncoder.encode(secret),
                request.name().trim(),
                trimToNull(request.description()),
                redirectUris,
                postLogoutRedirectUris,
                List.copyOf(scopes),
                request.publicClient());
        clientRepository.save(client);
        return new OAuthDtos.CreatedClient(OAuthDtos.ClientSummary.from(client), secret);
    }

    @Transactional
    public OAuthDtos.ClientSummary update(Long id, OAuthAdminRequests.UpdateClient request) {
        OAuthClient client = requireClient(id);
        boolean targetPublic = request.publicClient() == null
                ? client.isPublicClient() : request.publicClient();
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw ApiException.badRequest("INVALID_CLIENT_NAME", "应用名称不能为空");
            }
            client.setName(request.name().trim());
        }
        if (request.description() != null) {
            client.setDescription(trimToNull(request.description()));
        }
        if (request.redirectUris() != null || request.publicClient() != null) {
            List<String> redirectUris = request.redirectUris() == null
                    ? client.getRedirectUris() : request.redirectUris();
            client.setRedirectUris(validateRedirectUris(redirectUris, targetPublic));
        }
        if (request.postLogoutRedirectUris() != null || request.publicClient() != null) {
            List<String> postLogoutRedirectUris = request.postLogoutRedirectUris() == null
                    ? client.getPostLogoutRedirectUris() : request.postLogoutRedirectUris();
            client.setPostLogoutRedirectUris(
                    validatePostLogoutRedirectUris(postLogoutRedirectUris, targetPublic));
        }
        if (request.scopes() != null) {
            client.setScopes(List.copyOf(validateScopes(request.scopes())));
        }
        if (request.publicClient() != null) {
            boolean publicClient = request.publicClient();
            if (!publicClient && client.getClientSecretHash() == null) {
                throw ApiException.badRequest("SECRET_REQUIRED", "机密客户端必须先生成客户端密钥");
            }
            client.setPublicClient(publicClient);
        }
        if (request.enabled() != null) {
            client.setEnabled(request.enabled());
        }
        return OAuthDtos.ClientSummary.from(client);
    }

    @Transactional
    public OAuthDtos.CreatedClient rotateSecret(Long id) {
        OAuthClient client = requireClient(id);
        if (client.isPublicClient()) {
            throw ApiException.badRequest("PUBLIC_CLIENT", "公共客户端不使用客户端密钥");
        }
        String secret = "fns_" + randomToken(32);
        client.setClientSecretHash(passwordEncoder.encode(secret));
        return new OAuthDtos.CreatedClient(OAuthDtos.ClientSummary.from(client), secret);
    }

    private OAuthClient requireClient(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("OAUTH_CLIENT_NOT_FOUND", "OAuth 应用不存在"));
    }

    private static List<String> validateRedirectUris(List<String> values, boolean publicClient) {
        if (values == null || values.isEmpty()) {
            throw ApiException.badRequest("REDIRECT_URI_REQUIRED", "至少需要一个回调地址");
        }
        List<String> normalized = values.stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw ApiException.badRequest("REDIRECT_URI_REQUIRED", "至少需要一个回调地址");
        }
        validateCombinedLength(normalized, "回调地址总长度不能超过 4000 个字符", "INVALID_REDIRECT_URI");
        for (String value : normalized) {
            validateRedirectUri(value, publicClient, "INVALID_REDIRECT_URI");
        }
        return normalized;
    }

    private static List<String> validatePostLogoutRedirectUris(List<String> values, boolean publicClient) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> normalized = values.stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
        validateCombinedLength(normalized, "登出回调地址总长度不能超过 4000 个字符",
                "INVALID_POST_LOGOUT_REDIRECT_URI");
        for (String value : normalized) {
            validateRedirectUri(value, publicClient, "INVALID_POST_LOGOUT_REDIRECT_URI");
        }
        return normalized;
    }

    private static void validateRedirectUri(String value, boolean allowPrivateScheme, String errorCode) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            boolean webScheme = scheme.equals("https") || scheme.equals("http");
            if (webScheme) {
                String host = uri.getHost();
                if (host == null || host.isBlank()) {
                    throw ApiException.badRequest(errorCode, "回调地址缺少有效主机名：" + value);
                }
                if (scheme.equals("http") && !isLoopbackHost(host)) {
                    throw ApiException.badRequest(errorCode, "非本机回调地址必须使用 https：" + value);
                }
            } else {
                if (!allowPrivateScheme) {
                    throw ApiException.badRequest(errorCode,
                            "机密客户端只能使用 http 或 https 回调地址：" + value);
                }
                if (scheme.isBlank() || DANGEROUS_SCHEMES.contains(scheme)) {
                    throw ApiException.badRequest(errorCode, "不支持的私有回调协议：" + value);
                }
                if (uri.getSchemeSpecificPart() == null || uri.getSchemeSpecificPart().isBlank()) {
                    throw ApiException.badRequest(errorCode, "私有协议回调地址缺少目标路径：" + value);
                }
            }
            if (uri.getRawUserInfo() != null) {
                throw ApiException.badRequest(errorCode, "回调地址不能包含用户信息：" + value);
            }
            if (uri.getFragment() != null) {
                throw ApiException.badRequest(errorCode, "回调地址不能包含片段标识：" + value);
            }
            if (value.length() > 2000) {
                throw ApiException.badRequest(errorCode, "回调地址过长");
            }
        } catch (URISyntaxException exception) {
            throw ApiException.badRequest(errorCode, "回调地址格式不正确：" + value);
        }
    }

    private static void validateCombinedLength(List<String> values, String message, String errorCode) {
        if (String.join("\n", values).length() > 4000) {
            throw ApiException.badRequest(errorCode, message);
        }
    }

    private static boolean isLoopbackHost(String host) {
        return host.equalsIgnoreCase("localhost")
                || host.equals("127.0.0.1")
                || host.equals("[::1]")
                || host.equals("::1");
    }

    private static LinkedHashSet<String> validateScopes(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            throw ApiException.badRequest("INVALID_SCOPE", "至少需要声明一个权限范围");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String scope : scopes) {
            if (scope == null || scope.isBlank()) {
                continue;
            }
            normalized.add(scope.trim());
        }
        try {
            OAuthScopes.validateRegistered(normalized);
        } catch (OAuthException exception) {
            throw ApiException.badRequest("INVALID_SCOPE", exception.getMessage());
        }
        return normalized;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String randomToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
