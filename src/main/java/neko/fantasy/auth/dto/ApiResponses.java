package neko.fantasy.auth.dto;

import neko.fantasy.auth.domain.Role;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ApiResponses {

    private ApiResponses() {
    }

    public record User(Long id, String username, String nickname, String email, String phone, String displayName,
                       String avatarUrl, Role role, boolean enabled, Instant createdAt, Instant updatedAt,
                       Instant lastLoginAt) {

        public static User from(neko.fantasy.auth.domain.User user) {
            return new User(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(), user.getPhone(),
                    user.publicName(), neko.fantasy.auth.service.AvatarService.avatarUrl(user.getId()),
                    user.getRole(), user.isEnabled(),
                    user.getCreatedAt(), user.getUpdatedAt(), user.getLastLoginAt());
        }
    }

    public record Auth(String accessToken, String tokenType, long expiresIn, User user) {
    }

    public record Session(Long id, String ipAddress, String userAgent, SessionDevice device,
                          Instant createdAt, Instant lastUsedAt, Instant expiresAt, boolean current) {
    }

    public record SessionDevice(String type, String platform, String platformVersion,
                                String browser, String browserVersion) {
    }

    public record DeviceInfo(String type, String typeConfidence, String formFactor,
                             String platform, String platformVersion, String platformConfidence,
                             String architecture, String architectureClaim, String bitness,
                             String model, String modelSource, String browser,
                             String browserVersion, String screen, String screenCss,
                             String screenAvailable, String viewport, String orientation,
                             Integer colorDepth, Double pixelRatio, Integer refreshRate,
                             Boolean multipleScreens, String displayMode, String timezone,
                             String languages, Boolean touch, Integer maxTouchPoints,
                             String pointer, Boolean hover, Double deviceMemory,
                             Integer hardwareConcurrency, String gpuVendor, String gpuRenderer,
                             Double batteryLevel, Boolean batteryCharging, String connectionType,
                             String effectiveType, Double downlinkMbps, Integer rttMs,
                             Boolean saveData, Boolean webdriver, String typeEvidence,
                             String platformEvidence, String source, RequestSignals request) {

        public SessionDevice summary() {
            return new SessionDevice(type, platform, platformVersion, browser, browserVersion);
        }
    }

    /** 服务端从本次请求头读取的信号，用于和客户端上报值交叉校验。 */
    public record RequestSignals(String clientHintPlatform, String clientHintPlatformVersion,
                                 String clientHintModel, String clientHintFormFactors,
                                 String clientHintArchitecture, String clientHintBitness,
                                 String clientHintMobile, String clientHintUa,
                                 String clientHintFullVersionList, String clientHintWow64,
                                 String acceptLanguage,
                                 Boolean dnt, Boolean gpc, Boolean via, Boolean forwarded,
                                 Boolean hintMismatch) {
    }

    public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }

    public record AuditLog(Instant occurredAt, String operatorName, String targetName,
                           String actionLabel, String summary, String sourceAddress) {
        public static AuditLog from(neko.fantasy.auth.domain.AuditLog log) {
            return new AuditLog(log.getCreatedAt(), log.getActorUsername(), log.getTargetUsername(),
                    readableAction(log.getAction()), readableDetails(log.getDetails()), log.getIpAddress());
        }

        private static String readableAction(String action) {
            return switch (action) {
                case "USER_UPDATED", "账户设置变更" -> "账户设置变更";
                default -> action == null || action.isBlank() ? "后台操作" : action;
            };
        }

        private static String readableDetails(String details) {
            if (details == null || details.isBlank()) return "账户信息已更新";
            if (!details.contains("role:") && !details.contains("enabled:")) return details;

            return java.util.Arrays.stream(details.split(",\\s*"))
                    .map(ApiResponses.AuditLog::readableChange)
                    .collect(java.util.stream.Collectors.joining("；"));
        }

        private static String readableChange(String change) {
            if (change.startsWith("role:")) {
                String[] values = change.substring("role:".length()).trim().split("\\s*->\\s*");
                return values.length == 2
                        ? "角色从" + roleLabel(values[0]) + "调整为" + roleLabel(values[1])
                        : change;
            }
            if (change.startsWith("enabled:")) {
                String[] values = change.substring("enabled:".length()).trim().split("\\s*->\\s*");
                return values.length == 2
                        ? "账户状态从" + enabledLabel(values[0]) + "改为" + enabledLabel(values[1])
                        : change;
            }
            return change;
        }

        private static String roleLabel(String role) {
            return switch (role.trim()) {
                case "SUPER_ADMIN", "超管" -> "超管";
                case "ADMIN", "管理员" -> "管理员";
                case "USER", "普通成员" -> "普通成员";
                default -> role.trim();
            };
        }

        private static String enabledLabel(String enabled) {
            return "true".equalsIgnoreCase(enabled.trim()) || "启用".equals(enabled.trim()) ? "启用" : "停用";
        }
    }

    public record Message(String message) {
    }

    public record VerificationCodeSent(String message, long expiresInSeconds, long resendAfterSeconds) {
    }

    public record PasswordResetOptions(boolean smsEnabled) {
    }

    public record RegistrationCaptcha(String id, String image, long expiresInSeconds) {
    }

    public record Error(Instant timestamp, int status, String code, String message,
                        Map<String, String> fields) {
    }
}
