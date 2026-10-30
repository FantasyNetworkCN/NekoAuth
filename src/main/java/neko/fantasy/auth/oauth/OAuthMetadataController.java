package neko.fantasy.auth.oauth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class OAuthMetadataController {

    private final OAuthKeyService keyService;
    private final OAuthUserInfoService userInfoService;

    public OAuthMetadataController(OAuthKeyService keyService, OAuthUserInfoService userInfoService) {
        this.keyService = keyService;
        this.userInfoService = userInfoService;
    }

    @GetMapping({"/.well-known/openid-configuration", "/.well-known/oauth-authorization-server"})
    public Map<String, Object> discovery() {
        String issuer = keyService.getIssuer();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("issuer", issuer);
        metadata.put("authorization_endpoint", issuer + "/oauth2/authorize");
        metadata.put("token_endpoint", issuer + "/oauth2/token");
        metadata.put("userinfo_endpoint", issuer + "/oauth2/userinfo");
        metadata.put("jwks_uri", issuer + "/oauth2/jwks");
        metadata.put("revocation_endpoint", issuer + "/oauth2/revoke");
        metadata.put("introspection_endpoint", issuer + "/oauth2/introspect");
        metadata.put("end_session_endpoint", issuer + "/oauth2/logout");
        metadata.put("scopes_supported", OAuthScopes.all().stream().map(OAuthScopes.Definition::name).toList());
        metadata.put("response_types_supported", List.of("code"));
        metadata.put("response_modes_supported", List.of("query"));
        metadata.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
        metadata.put("subject_types_supported", List.of("public"));
        metadata.put("id_token_signing_alg_values_supported", List.of("RS256"));
        metadata.put("token_endpoint_auth_methods_supported",
                List.of("client_secret_basic", "client_secret_post", "none"));
        metadata.put("revocation_endpoint_auth_methods_supported",
                List.of("client_secret_basic", "client_secret_post", "none"));
        metadata.put("introspection_endpoint_auth_methods_supported",
                List.of("client_secret_basic", "client_secret_post", "none"));
        metadata.put("code_challenge_methods_supported", List.of("S256"));
        metadata.put("prompt_values_supported", List.of("none", "login", "consent", "select_account"));
        metadata.put("claims_supported", List.of(
                "sub", "iss", "aud", "exp", "iat", "auth_time", "nonce", "at_hash",
                "preferred_username", "name", "email", "email_verified", "role", "updated_at"));
        metadata.put("authorization_response_iss_parameter_supported", true);
        metadata.put("request_parameter_supported", false);
        metadata.put("request_uri_parameter_supported", false);
        metadata.put("claims_parameter_supported", false);
        metadata.put("ui_locales_supported", List.of("zh-CN", "en"));
        return metadata;
    }

    @GetMapping("/oauth2/jwks")
    public Map<String, Object> jwks() {
        return keyService.jwks();
    }
}
