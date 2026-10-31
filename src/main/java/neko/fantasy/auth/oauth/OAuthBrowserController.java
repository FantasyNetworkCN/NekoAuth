package neko.fantasy.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Controller
public class OAuthBrowserController {

    @GetMapping("/oauth2/authorize")
    public ResponseEntity<Void> authorize(HttpServletRequest request) {
        String query = request.getQueryString();
        String target = "/authorize" + (query == null || query.isBlank() ? "" : "?" + query);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }

    @PostMapping("/oauth2/authorize")
    public ResponseEntity<Void> authorizePost(
            @RequestParam MultiValueMap<String, String> parameters
    ) {
        UriComponentsBuilder target = UriComponentsBuilder.fromPath("/authorize");
        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            for (String value : entry.getValue()) {
                target.queryParam(entry.getKey(), value);
            }
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target.build().encode().toUriString()))
                .build();
    }
}
