package neko.fantasy.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import neko.fantasy.auth.domain.KnownDevice;
import neko.fantasy.auth.domain.User;
import neko.fantasy.auth.dto.ApiResponses;
import neko.fantasy.auth.repository.KnownDeviceRepository;
import neko.fantasy.auth.security.ClientIpResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class DeviceTrustService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final KnownDeviceRepository devices;
    private final AuthCookieService cookies;
    private final DeviceProfileService deviceProfiles;
    private final ClientIpResolver clientIps;
    private final Duration trustTtl;

    public DeviceTrustService(
            KnownDeviceRepository devices,
            AuthCookieService cookies,
            DeviceProfileService deviceProfiles,
            ClientIpResolver clientIps,
            @Value("${auth.risk.trust-ttl:30d}") Duration trustTtl
    ) {
        this.devices = devices;
        this.cookies = cookies;
        this.deviceProfiles = deviceProfiles;
        this.clientIps = clientIps;
        this.trustTtl = trustTtl;
    }

    public KnownDevice find(User user, HttpServletRequest request) {
        return cookies.readDevice(request)
                .map(DeviceTrustService::hashToken)
                .flatMap(hash -> devices.findByUserIdAndTokenHash(user.getId(), hash))
                .orElse(null);
    }

    @Transactional
    public DeviceResolution record(User user, HttpServletRequest request, ApiResponses.DeviceInfo device,
                                   boolean verifiedByStepUp) {
        Instant now = Instant.now();
        String existingToken = cookies.readDevice(request).orElse(null);
        boolean issuedToken = existingToken == null;
        String rawToken = issuedToken ? newToken() : existingToken;
        String tokenHash = hashToken(rawToken);
        KnownDevice known = devices.findByUserIdAndTokenHashForUpdate(user.getId(), tokenHash)
                .orElseGet(() -> new KnownDevice(user, tokenHash, now));

        String profileHash = deviceProfiles.profileHash(device);
        known.touch(profileHash,
                device == null ? null : device.type(),
                device == null ? null : device.platform(),
                device == null ? null : device.browser(),
                clientIps.resolve(request),
                truncate(request.getHeader("User-Agent"), 512),
                now);
        if (verifiedByStepUp) {
            known.trustAfterStepUp(now, trustTtl);
        }
        devices.saveAndFlush(known);
        return new DeviceResolution(issuedToken ? rawToken : null, known.isTrusted(now), known.getProfileHash());
    }

    @Transactional
    public void revokeTrust(Long userId) {
        devices.revokeTrustForUser(userId);
    }

    @Scheduled(cron = "0 42 4 * * *")
    @Transactional
    void deleteStaleDevices() {
        devices.deleteStale(Instant.now().minus(Duration.ofDays(365)));
    }

    static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record DeviceResolution(String newToken, boolean trusted, String profileHash) {
    }
}
