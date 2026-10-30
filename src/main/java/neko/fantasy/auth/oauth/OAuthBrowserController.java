package neko.fantasy.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.net.URI;

@Controller
public class OAuthBrowserController {

    @GetMapping("/oauth2/authorize")
    public ResponseEntity<Void> authorize(HttpServletRequest request) {
        String query = request.getQueryString();
        String target = "/authorize" + (query == null || query.isBlank() ? "" : "?" + query);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }
}
