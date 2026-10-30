package neko.fantasy.auth.service;

import neko.fantasy.auth.domain.User;
import neko.fantasy.auth.dto.AuthRequests;
import neko.fantasy.auth.dto.InternalAccountRequests;
import neko.fantasy.auth.dto.InternalAccountResponses;
import org.springframework.stereotype.Service;

/**
 * 第一方服务端账号桥接。
 *
 * <p>不创建 NekoAuth 浏览器会话，只返回账号身份；NekoMusic 继续使用自己的 Redis 用户令牌。
 * 密码、邮箱验证码、注册策略和会话撤销仍以 NekoAuth 为唯一权威源。</p>
 */
@Service
public class InternalAccountService {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    public InternalAccountService(AuthService authService,
                                  EmailVerificationService emailVerificationService) {
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
    }

    public InternalAccountResponses.Account login(InternalAccountRequests.Login request) {
        User user = authService.authenticateAccount(request.email(), request.password());
        return InternalAccountResponses.Account.from(user);
    }

    public void sendRegistrationCode(String email) {
        emailVerificationService.sendRegistrationCode(email);
    }

    public InternalAccountResponses.Account register(InternalAccountRequests.Register request) {
        User user = authService.registerAccount(new AuthRequests.Register(
                null, request.email(), request.password(), request.verificationCode()));
        return InternalAccountResponses.Account.from(user);
    }

    public InternalAccountResponses.Account adminCreate(InternalAccountRequests.AdminCreate request) {
        User user = authService.createAccountForAdmin(request.email(), request.password());
        return InternalAccountResponses.Account.from(user);
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
}
