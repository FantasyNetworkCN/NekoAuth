package neko.fantasy.auth.oauth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(basePackages = "neko.fantasy.auth.oauth")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OAuthEndpointExceptionHandler {

    @ExceptionHandler(OAuthException.class)
    public ResponseEntity<Map<String, Object>> handle(OAuthException exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", exception.getError());
        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            body.put("error_description", exception.getMessage());
        }
        if (exception.getError().equals("invalid_client")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"fantasynetwork-oauth\"")
                    .body(body);
        }
        if (exception.getError().equals("invalid_token")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"")
                    .body(body);
        }
        return ResponseEntity.status(exception.getStatus()).body(body);
    }
}
