package neko.fantasy.auth.web;

import neko.fantasy.auth.exception.ApiException;
import neko.fantasy.auth.oauth.OAuthClientCorsOrigins;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

/** 登录页完成后的第一方 Web 回跳校验；不进入 OAuth 授权码流程。 */
@RestController
@RequestMapping("/api/auth/web-entry")
public class WebEntryController {

    private final OAuthClientCorsOrigins clientOrigins;

    public WebEntryController(OAuthClientCorsOrigins clientOrigins) {
        this.clientOrigins = clientOrigins;
    }

    @PostMapping("/resolve")
    public Map<String, String> resolve(@RequestBody ResolveRequest request) {
        String raw = request.returnTo() == null ? "" : request.returnTo().trim();
        URI target;
        try {
            target = URI.create(raw);
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest("INVALID_RETURN_TO", "返回地址格式不正确");
        }
        String scheme = target.getScheme() == null ? "" : target.getScheme().toLowerCase(Locale.ROOT);
        if (target.getHost() == null || (!"https".equals(scheme) && !"http".equals(scheme))
                || target.getRawUserInfo() != null) {
            throw ApiException.badRequest("INVALID_RETURN_TO", "返回地址格式不正确");
        }
        if (!clientOrigins.contains(origin(target))) {
            throw ApiException.badRequest("INVALID_RETURN_TO", "返回地址不属于已登记的第一方应用");
        }
        return Map.of("returnTo", target.toString());
    }

    private static String origin(URI uri) {
        String host = uri.getHost();
        if (host.contains(":")) {
            host = "[" + host + "]";
        }
        return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + host
                + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
    }

    public record ResolveRequest(String returnTo) {
    }
}
