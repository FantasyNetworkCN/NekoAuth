package neko.fantasy.auth.service;

import neko.fantasy.auth.domain.User;
import neko.fantasy.auth.dto.AuthRequests;
import neko.fantasy.auth.dto.InternalAccountRequests;
import neko.fantasy.auth.dto.InternalAccountResponses;
import neko.fantasy.auth.internal.InternalTokenService;
import neko.fantasy.auth.oauth.OAuthClient;
import org.springframework.stereotype.Service;

/**
 * 第一方服务端账号桥接。
 *
 * <p>密码、邮箱验证码、注册策略、用户令牌签发和会话撤销均以 NekoAuth 为唯一权威源。
 * 第一方服务只保存 NekoAuth 返回的账号映射，不自行生成用户令牌。</p>
 */
@Service
public class InternalAccountService {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final InternalTokenService tokenService;

    public InternalAccountService(AuthService authService,
                                  EmailVerificationService emailVerificationService,
                                  InternalTokenService tokenService) {
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
        this.tokenService = tokenService;
    }

    public InternalAccountResponses.Account login(InternalAccountRequests.Login request, OAuthClient client) {
        User user = authService.authenticateAccount(request.email(), request.password());
        return InternalAccountResponses.Account.from(user, tokenService.issue(user, client));
    }

    public void sendRegistrationCode(String email) {
        emailVerificationService.sendRegistrationCode(email);
    }

    public InternalAccountResponses.Account register(InternalAccountRequests.Register request, OAuthClient client) {
        User user = authService.registerAccount(new AuthRequests.Register(
                null, request.nickname(), request.email(), request.password(),
                request.verificationCode(), null));
        return InternalAccountResponses.Account.from(user, tokenService.issue(user, client));
    }

    public InternalAccountResponses.Account adminCreate(InternalAccountRequests.AdminCreate request) {
        User user = authService.createAccountForAdmin(request.email(), request.password(), request.nickname());
        return InternalAccountResponses.Account.from(user);
    }

    public void updateNickname(InternalAccountRequests.UpdateNickname request) {
        authService.updateNicknameByEmail(request.email(), new AuthRequests.UpdateProfile(request.nickname()));
    }

    public void sendPasswordResetCode(String email) {
        authService.sendPasswordResetCode(email);
    }

    public void resetPassword(InternalAccountRequests.ResetPassword request) {
        authService.resetPassword(new AuthRequests.ResetPassword(
                request.email(), request.code(), request.newPassword()));
    }

    public void changePassword(InternalAccountRequests.ChangePassword request) {
        authService.changePasswordByEmail(request.email(), new AuthRequests.ChangePassword(
                request.currentPassword(), request.newPassword()));
    }

    public void setPassword(InternalAccountRequests.SetPassword request) {
        authService.setPasswordByEmail(request.email(), request.newPassword());
    }

    public InternalTokenService.IssuedToken exchangeToken(OAuthClient client, String accessToken) {
        return tokenService.exchange(client, accessToken);
    }
}
