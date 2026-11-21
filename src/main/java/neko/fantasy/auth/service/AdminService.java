package neko.fantasy.auth.service;

import neko.fantasy.auth.domain.Role;
import neko.fantasy.auth.domain.User;
import neko.fantasy.auth.dto.ApiResponses;
import neko.fantasy.auth.dto.AuthRequests;
import neko.fantasy.auth.exception.ApiException;
import neko.fantasy.auth.repository.RefreshTokenRepository;
import neko.fantasy.auth.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogService auditLogService;

    public AdminService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                        AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public ApiResponses.Page<ApiResponses.User> listUsers(String query, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        PageRequest pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Page<User> users = normalizedQuery.isEmpty()
                ? userRepository.findAll(pageable)
                : userRepository.search(normalizedQuery, pageable);

        return new ApiResponses.Page<>(
                users.getContent().stream().map(ApiResponses.User::from).toList(),
                users.getNumber(), users.getSize(), users.getTotalElements(), users.getTotalPages());
    }

    @Transactional
    public ApiResponses.User updateUser(Long actorId, String actorUsername, String ipAddress,
                                        Long userId, AuthRequests.AdminUpdateUser request) {
        if (request.enabled() == null && request.role() == null) {
            throw ApiException.badRequest("NOTHING_TO_UPDATE", "至少需要提供一个要更新的字段");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "用户不存在"));

        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> ApiException.unauthorized("USER_NOT_FOUND", "操作者不存在"));
        if (actor.getRole() == Role.ADMIN
                && (user.getRole() == Role.ADMIN || user.getRole() == Role.SUPER_ADMIN)) {
            throw ApiException.forbidden("ADMIN_ACCOUNT_PROTECTED", "普通管理员不能编辑超管或其他管理员账户");
        }
        if (actor.getRole() != Role.SUPER_ADMIN && request.role() == Role.SUPER_ADMIN) {
            throw ApiException.forbidden("SUPER_ADMIN_REQUIRED", "只有超管可以设置超管角色");
        }
        if (actorId.equals(userId)) {
            if (Boolean.FALSE.equals(request.enabled())) {
                throw ApiException.badRequest("CANNOT_DISABLE_SELF", "不能停用自己的账户");
            }
            if (request.role() != null && request.role() != user.getRole()) {
                throw ApiException.badRequest("CANNOT_DEMOTE_SELF", "不能修改自己的管理员权限");
            }
        }
        if (user.getRole() == Role.SUPER_ADMIN
                && (Boolean.FALSE.equals(request.enabled())
                || (request.role() != null && request.role() != Role.SUPER_ADMIN))) {
            throw ApiException.forbidden("SUPER_ADMIN_PROTECTED", "唯一超管账户不能被停用或降级");
        }
        if (request.role() == Role.SUPER_ADMIN && user.getRole() != Role.SUPER_ADMIN) {
            throw ApiException.badRequest("SINGLE_SUPER_ADMIN", "系统只保留一个超管账户");
        }

        Role oldRole = user.getRole();
        boolean oldEnabled = user.isEnabled();
        boolean invalidateSessions = false;
        if (request.role() != null && request.role() != user.getRole()) {
            user.setRole(request.role());
            invalidateSessions = true;
        }
        if (request.enabled() != null && request.enabled() != user.isEnabled()) {
            user.setEnabled(request.enabled());
            invalidateSessions = invalidateSessions || !request.enabled();
        }

        if (invalidateSessions) {
            user.incrementTokenVersion();
            userRepository.saveAndFlush(user);
            refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
            ApiResponses.User response = ApiResponses.User.from(user);
            auditLogService.record(actorId, actorUsername, "USER_UPDATED", user.getId(), user.getUsername(),
                    "role: " + oldRole + " -> " + user.getRole() + ", enabled: " + oldEnabled + " -> " + user.isEnabled(),
                    ipAddress);
            return response;
        }

        ApiResponses.User response = ApiResponses.User.from(userRepository.save(user));
        auditLogService.record(actorId, actorUsername, "USER_UPDATED", user.getId(), user.getUsername(),
                "role: " + oldRole + " -> " + user.getRole() + ", enabled: " + oldEnabled + " -> " + user.isEnabled(),
                ipAddress);
        return response;
    }
}
