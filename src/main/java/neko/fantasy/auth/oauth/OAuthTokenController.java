package neko.fantasy.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/oauth2")
public class OAuthTokenController {

    private final OAuthTokenEndpointService tokenEndpointService;

    public OAuthTokenController(OAuthTokenEndpointService tokenEndpointService) {
        this.tokenEndpointService = tokenEndpointService;
    }

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> token(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            HttpServletRequest request
    ) {
        Map<String, String> form = form(request);
        return tokenEndpointService.token(form, authorization);
    }

    @PostMapping(value = "/revoke", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> revoke(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            HttpServletRequest request
    ) {
        tokenEndpointService.revoke(form(request), authorization);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/introspect", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> introspect(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            HttpServletRequest request
    ) {
        return tokenEndpointService.introspect(form(request), authorization);
    }

    private static Map<String, String> form(HttpServletRequest request) {
        Map<String, String> form = new LinkedHashMap<>();
        request.getParameterMap().forEach((name, values) -> {
            if (values.length > 0) {
                form.put(name, values[0]);
            }
        });
        return form;
    }
}
