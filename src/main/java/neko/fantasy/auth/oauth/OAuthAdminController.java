package neko.fantasy.auth.oauth;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/oauth-clients")
public class OAuthAdminController {

    private final OAuthClientAdminService clientAdminService;

    public OAuthAdminController(OAuthClientAdminService clientAdminService) {
        this.clientAdminService = clientAdminService;
    }

    @GetMapping
    public List<OAuthDtos.ClientSummary> list() {
        return clientAdminService.list();
    }

    @GetMapping("/scopes")
    public List<OAuthScopes.Definition> scopes() {
        return OAuthScopes.allForAdministration();
    }

    @PostMapping
    public OAuthDtos.CreatedClient create(
            @Valid @RequestBody OAuthAdminRequests.CreateClient request
    ) {
        return clientAdminService.create(request);
    }

    @PatchMapping("/{id}")
    public OAuthDtos.ClientSummary update(
            @PathVariable Long id,
            @Valid @RequestBody OAuthAdminRequests.UpdateClient request
    ) {
        return clientAdminService.update(id, request);
    }

    @PostMapping("/{id}/secret")
    public OAuthDtos.CreatedClient rotateSecret(@PathVariable Long id) {
        return clientAdminService.rotateSecret(id);
    }
}
