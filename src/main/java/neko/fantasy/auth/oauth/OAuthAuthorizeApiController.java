package neko.fantasy.auth.oauth;

import neko.fantasy.auth.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/oauth/authorize")
public class OAuthAuthorizeApiController {

    private final OAuthAuthorizationService authorizationService;

    public OAuthAuthorizeApiController(OAuthAuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public OAuthDtos.AuthorizeView authorize(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String client_id,
            @RequestParam(required = false) String redirect_uri,
            @RequestParam(required = false) String response_type,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String nonce,
            @RequestParam(required = false) String code_challenge,
            @RequestParam(required = false) String code_challenge_method,
            @RequestParam(required = false) String prompt,
            @RequestParam(required = false) String login_hint,
            @RequestParam(required = false) String max_age
    ) {
        OAuthDtos.AuthorizationRequest authorizationRequest = request(
                client_id, redirect_uri, response_type, scope, state, nonce,
                code_challenge, code_challenge_method, prompt, login_hint, max_age);
        if (principal == null) {
            return authorizationService.inspectAnonymous(authorizationRequest);
        }
        return authorizationService.inspect(
                principal.id(), principal.authenticatedAt(), principal.sessionFamilyId(),
                authorizationRequest);
    }

    @PostMapping("/decision")
    public Map<String, String> decision(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Decision decision
    ) {
        String redirectUrl = authorizationService.decide(
                principal.id(), principal.authenticatedAt(), principal.sessionFamilyId(),
                request(decision.clientId(), decision.redirectUri(), decision.responseType(),
                        decision.scope(), decision.state(), decision.nonce(), decision.codeChallenge(),
                        decision.codeChallengeMethod(), decision.prompt(), decision.loginHint(),
                        decision.maxAge()),
                decision.approved());
        return Map.of("redirectUrl", redirectUrl);
    }

    private static OAuthDtos.AuthorizationRequest request(
            String clientId, String redirectUri, String responseType, String scope, String state,
            String nonce, String codeChallenge, String codeChallengeMethod, String prompt,
            String loginHint, String maxAge) {
        return new OAuthDtos.AuthorizationRequest(
                clientId, redirectUri, responseType, scope, state, nonce,
                codeChallenge, codeChallengeMethod, prompt, loginHint, maxAge);
    }

    public record Decision(
            String clientId,
            String redirectUri,
            String responseType,
            String scope,
            String state,
            String nonce,
            String codeChallenge,
            String codeChallengeMethod,
            String prompt,
            String loginHint,
            String maxAge,
            boolean approved
    ) {
    }
}
