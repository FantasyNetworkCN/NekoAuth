package neko.fantasy.auth.oauth;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class OAuthDtos {

    private OAuthDtos() {
    }

    public record AuthorizationRequest(
            String clientId,
            String redirectUri,
            String responseType,
            String scope,
            String state,
            String nonce,
            String codeChallenge,
            String codeChallengeMethod,
            String prompt
    ) {
    }

    public record AuthorizeView(
            String clientId,
            String clientName,
            String description,
            String redirectHost,
            String username,
            String displayName,
            Set<String> scopes,
            List<OAuthScopes.Definition> scopeDetails,
            boolean previouslyApproved,
            String redirectUrl
    ) {
    }

    public record ClientSummary(
            Long id,
            String clientId,
            String name,
            String description,
            List<String> redirectUris,
            List<String> scopes,
            boolean publicClient,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static ClientSummary from(OAuthClient client) {
            return new ClientSummary(client.getId(), client.getClientId(), client.getName(),
                    client.getDescription(), client.getRedirectUris(), client.getScopes(),
                    client.isPublicClient(), client.isEnabled(), client.getCreatedAt(),
                    client.getUpdatedAt());
        }
    }

    public record CreatedClient(ClientSummary client, String clientSecret) {
    }
}
