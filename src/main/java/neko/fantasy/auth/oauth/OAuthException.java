package neko.fantasy.auth.oauth;

import org.springframework.http.HttpStatus;

public class OAuthException extends RuntimeException {

    private final String error;
    private final HttpStatus status;

    public OAuthException(String error, String description, HttpStatus status) {
        super(description);
        this.error = error;
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static OAuthException invalidRequest(String description) {
        return new OAuthException("invalid_request", description, HttpStatus.BAD_REQUEST);
    }

    public static OAuthException invalidClient(String description) {
        return new OAuthException("invalid_client", description, HttpStatus.UNAUTHORIZED);
    }

    public static OAuthException invalidGrant(String description) {
        return new OAuthException("invalid_grant", description, HttpStatus.BAD_REQUEST);
    }

    public static OAuthException invalidScope(String description) {
        return new OAuthException("invalid_scope", description, HttpStatus.BAD_REQUEST);
    }

    public static OAuthException unsupportedGrantType(String description) {
        return new OAuthException("unsupported_grant_type", description, HttpStatus.BAD_REQUEST);
    }

    public static OAuthException accessDenied(String description) {
        return new OAuthException("access_denied", description, HttpStatus.FORBIDDEN);
    }
}
