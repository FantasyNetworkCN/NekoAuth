package neko.fantasy.auth.oauth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class OAuthUserInfoController {

    private final OAuthUserInfoService userInfoService;

    public OAuthUserInfoController(OAuthUserInfoService userInfoService) {
        this.userInfoService = userInfoService;
    }

    @GetMapping("/oauth2/userinfo")
    public ResponseEntity<Map<String, Object>> userInfo(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(userInfoService.userInfo(authorization));
    }

    @PostMapping("/oauth2/userinfo")
    public ResponseEntity<Map<String, Object>> userInfoPost(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return userInfo(authorization);
    }
}
