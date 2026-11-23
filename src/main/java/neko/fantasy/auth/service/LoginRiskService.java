package neko.fantasy.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import neko.fantasy.auth.domain.KnownDevice;
import neko.fantasy.auth.domain.User;
import neko.fantasy.auth.dto.ApiResponses;
import neko.fantasy.auth.repository.RefreshTokenRepository;
import neko.fantasy.auth.security.ClientIpResolver;
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
    private final ClientIpResolver clientIps;
    private final IpGeoService ipGeo;
    private final int stepUpThreshold;
    private final int denyThreshold;
    private final Duration historyWindow;

    public LoginRiskService(
            RefreshTokenRepository refreshTokens,
            DeviceProfileService deviceProfiles,
            ClientIpResolver clientIps,
            IpGeoService ipGeo,
            @Value("${auth.risk.step-up-threshold:50}") int stepUpThreshold,
            @Value("${auth.risk.deny-threshold:90}") int denyThreshold,
            @Value("${auth.risk.history-window:90d}") Duration historyWindow
    ) {
        this.refreshTokens = refreshTokens;
        this.deviceProfiles = deviceProfiles;
        this.clientIps = clientIps;
        this.ipGeo = ipGeo;
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

        String ipAddress = clientIps.resolve(request);
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

        IpGeoService.IpLocation currentLocation = ipGeo.resolve(ipAddress);
        var latest = refreshTokens.findFirstByUserIdOrderByCreatedAtDesc(user.getId()).orElse(null);
        boolean impossibleTravel = false;
        boolean recentCountryChange = false;
        if (latest != null) {
            IpGeoService.IpLocation previousLocation = latest.getLoginCountryCode() == null
                    ? ipGeo.resolve(latest.getIpAddress())
                    : new IpGeoService.IpLocation(
                    latest.getLoginCountryCode(), latest.getLoginCountryName(), latest.getLoginRegion(),
                    latest.getLoginCity(), latest.getLoginLatitude(), latest.getLoginLongitude(),
                    latest.getLoginAsn(), latest.getLoginAsnOrganization(), "history");
            Duration elapsed = Duration.between(latest.getCreatedAt(), now);
            boolean recent = !elapsed.isNegative() && elapsed.compareTo(Duration.ofHours(24)) <= 0;
            boolean countryChanged = currentLocation.hasCountry() && previousLocation.hasCountry()
                    && !currentLocation.countryCode().equalsIgnoreCase(previousLocation.countryCode());
            boolean regionChanged = currentLocation.hasCountry() && previousLocation.hasCountry()
                    && !countryChanged
                    && !currentLocation.region().isBlank()
                    && !previousLocation.region().isBlank()
                    && !currentLocation.region().equalsIgnoreCase(previousLocation.region());
            if (recent && countryChanged) {
                score += 30;
                recentCountryChange = true;
                reasons.add("country_changed");
            } else if (recent && regionChanged) {
                score += 15;
                reasons.add("region_changed");
            }
            double distance = IpGeoService.distanceKm(previousLocation, currentLocation);
            if (distance >= 500 && !elapsed.isNegative()) {
                double hours = Math.max(1.0 / 60.0, elapsed.toMillis() / 3_600_000.0);
                if (distance / hours > 900) {
                    impossibleTravel = true;
                    reasons.add("impossible_travel");
                }
            }
        }
        score = Math.max(0, Math.min(100, score));
        if (recentCountryChange) {
            score = Math.max(55, score);
        }
        if (impossibleTravel) {
            score = Math.max(75, score);
        }
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
