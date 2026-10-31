package neko.fantasy.auth.web;

import jakarta.validation.Valid;
import neko.fantasy.auth.dto.InternalAccountRequests;
import neko.fantasy.auth.dto.InternalAccountResponses;
import neko.fantasy.auth.internal.InternalClientAuthenticator;
import neko.fantasy.auth.internal.InternalTokenService;
import neko.fantasy.auth.oauth.OAuthClient;
import neko.fantasy.auth.service.InternalAccountService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * NekoMusic 等服务端应用使用的账号桥接接口。
 *
 * <p>路由不在 {@code /api/auth} 下，也不接受 JWT；每次调用都必须带指定机密客户端的
 * HTTP Basic 凭据。客户端密钥只存在服务端配置中。</p>
 */
@RestController
@RequestMapping("/api/internal/accounts")
public class InternalAccountController {

    private final InternalClientAuthenticator clientAuthenticator;
    private final InternalAccountService accountService;

    public InternalAccountController(InternalClientAuthenticator clientAuthenticator,
                                     InternalAccountService accountService) {
        this.clientAuthenticator = clientAuthenticator;
        this.accountService = accountService;
    }

    @PostMapping("/login")
    public InternalAccountResponses.Account login(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody InternalAccountRequests.Login request
    ) {
        OAuthClient client = clientAuthenticator.requireClient(authorization);
        return accountService.login(request, client);
    }

    @PostMapping("/register/code")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registrationCode(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody InternalAccountRequests.EmailRequest request
    ) {
        clientAuthenticator.requireClient(authorization);
        accountService.sendRegistrationCode(request.email());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public InternalAccountResponses.Account register(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody InternalAccountRequests.Register request
    ) {
        OAuthClient client = clientAuthenticator.requireClient(authorization);
        return accountService.register(request, client);
    }

    /** 由已校验管理员权限的后台调用，创建普通用户并跳过邮箱验证码。 */
    @PostMapping("/admin-create")
    @ResponseStatus(HttpStatus.CREATED)
    public InternalAccountResponses.Account adminCreate(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody InternalAccountRequests.AdminCreate request
    ) {
        clientAuthenticator.requireClient(authorization);
        return accountService.adminCreate(request);
    }

    /**
     * 把同一机密客户端签发的短期 OAuth access token 换成 NekoAuth 所有的长期第一方 token。
     * 仅供 NekoMusic 在同一次浏览器 SSO 回调中服务端调用。
     */
    @PostMapping("/token/exchange")
    public InternalTokenService.IssuedToken exchangeToken(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody InternalAccountRequests.ExchangeToken request
    ) {
        OAuthClient client = clientAuthenticator.requireClient(authorization);
        return accountService.exchangeToken(client, request.accessToken());
    }

    @PostMapping("/password/reset-code")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void passwordResetCode(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody InternalAccountRequests.EmailRequest request
    ) {
        clientAuthenticator.requireClient(authorization);
        accountService.sendPasswordResetCode(request.email());
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody InternalAccountRequests.ResetPassword request
    ) {
        clientAuthenticator.requireClient(authorization);
        accountService.resetPassword(request);
    }

    @PostMapping("/password/change")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody InternalAccountRequests.ChangePassword request
    ) {
        clientAuthenticator.requireClient(authorization);
        accountService.changePassword(request);
    }

    /** 仅由已校验权限的管理后台调用，用于管理员创建用户或重置任意用户密码。 */
    @PostMapping("/password/admin-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void adminResetPassword(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody InternalAccountRequests.SetPassword request
    ) {
        clientAuthenticator.requireClient(authorization);
        accountService.setPassword(request);
    }
}
