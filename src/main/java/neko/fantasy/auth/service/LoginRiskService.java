package neko.fantasy.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import neko.fantasy.auth.domain.KnownDevice;
import neko.fantasy.auth.domain.User;
import neko.fantasy.auth.dto.ApiResponses;
import neko.fantasy.auth.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class LoginRiskService {

    private final RefreshTokenRepository refreshTokens;
    private final DeviceProfileService deviceProfiles;
    private final int stepUpThreshold;
    private final int denyThreshold;
    private final Duration historyWindow;

    public LoginRiskService(
            RefreshTokenRepository refreshTokens,
            DeviceProfileService deviceProfiles,
            @Value("${auth.risk.step-up-threshold:50}") int stepUpThreshold,
            @Value("${auth.risk.deny-threshold:90}") int denyThreshold,
            @Value("${auth.risk.history-window:90d}") Duration historyWindow
    ) {
        this.refreshTokens = refreshTokens;
        this.deviceProfiles = deviceProfiles;
        this.stepUpThreshold = Math.max(1, Math.min(100, stepUpThreshold));
        this.denyThreshold = Math.max(this.stepUpThreshold + 1, Math.min(101, denyThreshold));
        this.historyWindow = historyWindow;
    }

    public RiskEvaluation evaluate(User user, ApiResponses.DeviceInfo device, boolean deviceTokenPresent,
                                   KnownDevice knownDevice, int recentFailures, HttpServletRequest request) {
        Instant now = Instant.now();
        List<String> reasons = new ArrayList<>();
        int score = 0;

        if (knownDevice == null) {
            score += 25;
            reasons.add("unknown_device");
        } else {
            String currentProfile = deviceProfiles.profileHash(device);
            if (knownDevice.getProfileHash() != null && currentProfile != null
                    && !knownDevice.getProfileHash().equals(currentProfile)) {
                score += 15;
                reasons.add("device_profile_changed");
            } else {
                score -= 15;
                reasons.add("known_device");
            }
            if (knownDevice.isTrusted(now)) {
                score -= 20;
                reasons.add("trusted_device");
            }
        }

        if (!deviceTokenPresent) {
            score += 10;
            reasons.add("device_cookie_missing");
        }

        String ipAddress = request.getRemoteAddr();
        boolean hasHistory = refreshTokens.existsByUserId(user.getId());
        boolean previousIp = ipAddress != null && refreshTokens
                .existsByUserIdAndIpAddressAndCreatedAtAfter(user.getId(), ipAddress, now.minus(historyWindow));
        if (hasHistory && !previousIp) {
            score += 10;
            reasons.add("new_ip");
        }

        if (recentFailures >= 3) {
            score += Math.min(20, recentFailures * 3);
            reasons.add("recent_password_failures");
        }
        if (deviceProfiles.isWebdriver(device)) {
            score += 35;
            reasons.add("webdriver");
        }
        if (deviceProfiles.hasHintMismatch(device)) {
            score += 25;
            reasons.add("client_hint_mismatch");
        }
        if (deviceProfiles.hasProxyHeader(device)) {
            score += 10;
            reasons.add("proxy_header");
        }
        if (deviceProfiles.isUncertain(device)) {
            score += 5;
            reasons.add("uncertain_device_profile");
        }
        score = Math.max(0, Math.min(100, score));
        boolean blocked = score >= denyThreshold;
        boolean stepUpRequired = score >= stepUpThreshold && !blocked;
        String level = score >= 90 ? "critical" : score >= 70 ? "high"
                : score >= stepUpThreshold ? "medium" : "low";
        return new RiskEvaluation(score, level, List.copyOf(reasons), stepUpRequired, blocked);
    }

    public record RiskEvaluation(int score, String level, List<String> reasons,
                                 boolean stepUpRequired, boolean blocked) {
        public String reasonText() {
            return String.join(",", reasons);
        }
    }
}
