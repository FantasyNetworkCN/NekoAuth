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

    public AdminService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
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
    public ApiResponses.User updateUser(Long actorId, Long userId, AuthRequests.AdminUpdateUser request) {
        if (request.enabled() == null && request.role() == null) {
            throw ApiException.badRequest("NOTHING_TO_UPDATE", "至少需要提供一个要更新的字段");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "用户不存在"));

        if (actorId.equals(userId)) {
            if (Boolean.FALSE.equals(request.enabled())) {
                throw ApiException.badRequest("CANNOT_DISABLE_SELF", "不能停用自己的账户");
            }
            if (request.role() != null && request.role() != Role.ADMIN) {
                throw ApiException.badRequest("CANNOT_DEMOTE_SELF", "不能移除自己的管理员权限");
            }
        }

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
            return ApiResponses.User.from(user);
        }

        return ApiResponses.User.from(userRepository.save(user));
    }
}
