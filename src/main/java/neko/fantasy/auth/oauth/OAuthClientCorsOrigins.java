package neko.fantasy.auth.oauth;

import org.springframework.stereotype.Component;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 从已启用 OAuth 客户端登记的浏览器回调地址推导 CORS Origin。
 * 这样客户端注册后即可从自己的站点访问发现文档、JWKS 与 OAuth 端点，无需额外维护来源白名单。
 */
@Component
public class OAuthClientCorsOrigins {

    private final OAuthClientRepository clientRepository;

    public OAuthClientCorsOrigins(OAuthClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    public boolean contains(String origin) {
        Optional<String> normalized = normalizeOrigin(origin);
        if (normalized.isEmpty()) {
            return false;
        }
        Set<String> registeredOrigins = new LinkedHashSet<>();
        for (OAuthClient client : clientRepository.findAllByEnabledTrue()) {
            client.getRedirectUris().forEach(value -> normalizeOrigin(value).ifPresent(registeredOrigins::add));
            client.getPostLogoutRedirectUris().forEach(
                    value -> normalizeOrigin(value).ifPresent(registeredOrigins::add));
        }
        return registeredOrigins.contains(normalized.get());
    }

    static Optional<String> normalizeOrigin(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return Optional.empty();
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return Optional.empty();
            }
            boolean ipv6 = host.indexOf(':') >= 0;
            if (host.startsWith("[") && host.endsWith("]")) {
                host = host.substring(1, host.length() - 1);
                ipv6 = true;
            }
            host = ipv6 ? host.toLowerCase(Locale.ROOT) : IDN.toASCII(host).toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            boolean defaultPort = port == -1
                    || (scheme.equals("http") && port == 80)
                    || (scheme.equals("https") && port == 443);
            String normalizedHost = ipv6 ? "[" + host + "]" : host;
            return Optional.of(scheme + "://" + normalizedHost + (defaultPort ? "" : ":" + port));
        } catch (URISyntaxException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
