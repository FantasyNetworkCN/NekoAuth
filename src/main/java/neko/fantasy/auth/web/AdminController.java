package neko.fantasy.auth.web;

import jakarta.validation.Valid;
import neko.fantasy.auth.dto.ApiResponses;
import neko.fantasy.auth.dto.AuthRequests;
import neko.fantasy.auth.security.UserPrincipal;
import neko.fantasy.auth.security.ClientIpResolver;
import neko.fantasy.auth.service.AdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final ClientIpResolver clientIps;

    public AdminController(AdminService adminService, ClientIpResolver clientIps) {
        this.adminService = adminService;
        this.clientIps = clientIps;
    }

    @GetMapping
    public ApiResponses.Page<ApiResponses.User> listUsers(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return adminService.listUsers(query, page, size);
    }

    @PatchMapping("/{id}")
    public ApiResponses.User updateUser(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest,
            @PathVariable Long id,
            @Valid @RequestBody AuthRequests.AdminUpdateUser request
    ) {
        return adminService.updateUser(
                principal.id(), principal.username(), clientIps.resolve(servletRequest), id, request);
    }
}
