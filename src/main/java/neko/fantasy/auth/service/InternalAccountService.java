package neko.fantasy.auth.service;

import neko.fantasy.auth.domain.User;
import neko.fantasy.auth.dto.AuthRequests;
import neko.fantasy.auth.dto.InternalAccountRequests;
import neko.fantasy.auth.dto.InternalAccountResponses;
import neko.fantasy.auth.internal.InternalTokenService;
import neko.fantasy.auth.oauth.OAuthClient;
import neko.fantasy.auth.repository.UserRepository;
import neko.fantasy.auth.exception.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 第一方服务端账号桥接。
 *
 * <p>第一方服务只负责在 NekoAuth 中维护账号映射、资料、头像、管理员操作与登录会话，
 * 不自行生成用户令牌。</p>
 */
@Service
public class InternalAccountService {

    private final AuthService authService;
    private final InternalTokenService tokenService;
    private final UserRepository userRepository;
    private final AvatarService avatarService;

    public InternalAccountService(AuthService authService,
                                  InternalTokenService tokenService,
                                  UserRepository userRepository,
                                  AvatarService avatarService) {
        this.authService = authService;
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.avatarService = avatarService;
    }

    public InternalAccountResponses.Account adminCreate(InternalAccountRequests.AdminCreate request) {
        User user = authService.createAccountForAdmin(request.email(), request.password(), request.nickname());
        return InternalAccountResponses.Account.from(user);
    }

    public InternalAccountResponses.Account loginSsoSession(String ssoSession, OAuthClient client) {
        User user = authService.findUserBySsoSession(ssoSession)
                .orElseThrow(() -> ApiException.unauthorized(
                        "SSO_SESSION_INVALID", "共享登录会话不存在或已失效"));
        return InternalAccountResponses.Account.from(
                user, tokenService.issue(user, client, ssoSession));
    }

    public void logoutSsoSession(String ssoSession) {
        authService.revokeSsoSession(ssoSession);
    }

    public void updateNickname(InternalAccountRequests.UpdateNickname request) {
        authService.updateNicknameByEmail(request.email(), new AuthRequests.UpdateProfile(request.nickname()));
    }

    public InternalAccountResponses.Summary uploadAvatar(long authUserId,
                                                         org.springframework.web.multipart.MultipartFile file) {
        return InternalAccountResponses.Summary.from(avatarService.upload(authUserId, file));
    }

    public List<InternalAccountResponses.Summary> lookup(InternalAccountRequests.Lookup request) {
        return userRepository.findAllById(request.authUserIds()).stream()
                .map(InternalAccountResponses.Summary::from)
                .toList();
    }

    public List<InternalAccountResponses.Summary> search(InternalAccountRequests.Search request) {
        return userRepository.search(request.query().trim(), PageRequest.of(0, 20))
                .map(InternalAccountResponses.Summary::from)
                .getContent();
    }

    public void setPassword(InternalAccountRequests.SetPassword request) {
        authService.setPasswordByEmail(request.email(), request.newPassword());
    }

    public InternalTokenService.IssuedToken exchangeToken(OAuthClient client, String accessToken) {
        return tokenService.exchange(client, accessToken);
    }
}
