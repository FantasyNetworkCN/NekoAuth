package neko.fantasy.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import neko.fantasy.auth.dto.ApiResponses;
import neko.fantasy.auth.dto.AuthRequests;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class DeviceProfileService {

    private final ObjectMapper objectMapper;

    public DeviceProfileService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ApiResponses.DeviceInfo capture(AuthRequests.DeviceProfile device, HttpServletRequest request) {
        if (device == null) return null;
        ApiResponses.DeviceInfo sanitized = new ApiResponses.DeviceInfo(
                deviceType(device.type()),
                confidence(device.typeConfidence()),
                clean(device.formFactor(), 64),
                clean(device.platform(), 40),
                clean(device.platformVersion(), 40),
                confidence(device.platformConfidence()),
                clean(device.architecture(), 24),
                clean(device.architectureClaim(), 24),
                clean(device.bitness(), 16),
                clean(device.model(), 80),
                clean(device.modelSource(), 32),
                clean(device.browser(), 40),
                clean(device.browserVersion(), 40),
                clean(device.screen(), 80),
                clean(device.screenCss(), 40),
                clean(device.screenAvailable(), 40),
                clean(device.viewport(), 40),
                clean(device.orientation(), 24),
                integer(device.colorDepth(), 1, 64),
                decimal(device.pixelRatio(), 0.1, 16),
                integer(device.refreshRate(), 1, 1000),
                device.multipleScreens(),
                clean(device.displayMode(), 32),
                clean(device.timezone(), 64),
                clean(device.languages(), 160),
                device.touch(),
                integer(device.maxTouchPoints(), 0, 100),
                pointerType(device.pointer()),
                device.hover(),
                decimal(device.deviceMemory(), 0.1, 1024),
                integer(device.hardwareConcurrency(), 1, 1024),
                clean(device.gpuVendor(), 120),
                clean(device.gpuRenderer(), 160),
                decimal(device.batteryLevel(), 0, 1),
                device.batteryCharging(),
                clean(device.connectionType(), 24),
                clean(device.effectiveType(), 16),
                decimal(device.downlinkMbps(), 0, 100000),
                integer(device.rttMs(), 0, 600000),
                device.saveData(),
                device.webdriver(),
                clean(device.typeEvidence(), 40),
                clean(device.platformEvidence(), 40),
                clean(device.source(), 40),
                requestSignals(device, request)
        );
        if (sanitized.type() == null && sanitized.platform() == null
                && sanitized.browser() == null && sanitized.model() == null) {
            return null;
        }
        return sanitized;
    }

    public String toJson(ApiResponses.DeviceInfo device) {
        if (device == null) return null;
        try {
            return objectMapper.writeValueAsString(device);
        } catch (JacksonException exception) {
            return null;
        }
    }

    public ApiResponses.DeviceInfo fromJson(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readValue(value, ApiResponses.DeviceInfo.class);
        } catch (JacksonException exception) {
            return null;
        }
    }

    public String profileHash(ApiResponses.DeviceInfo device) {
        if (device == null) return null;
        String stable = String.join("|",
                nullToEmpty(device.type()),
                nullToEmpty(device.formFactor()),
                nullToEmpty(device.platform()),
                nullToEmpty(device.architecture()),
                nullToEmpty(device.model()),
                nullToEmpty(device.browser()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(stable.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public boolean isUncertain(ApiResponses.DeviceInfo device) {
        return device == null
                || device.type() == null
                || "unknown".equalsIgnoreCase(device.type())
                || "low".equalsIgnoreCase(device.typeConfidence());
    }

    public boolean isWebdriver(ApiResponses.DeviceInfo device) {
        return device != null && Boolean.TRUE.equals(device.webdriver());
    }

    public boolean hasProxyHeader(ApiResponses.DeviceInfo device) {
        return device != null && device.request() != null
                && (Boolean.TRUE.equals(device.request().via())
                || Boolean.TRUE.equals(device.request().forwarded()));
    }

    public boolean hasHintMismatch(ApiResponses.DeviceInfo device) {
        return device != null && device.request() != null
                && Boolean.TRUE.equals(device.request().hintMismatch());
    }

    private static ApiResponses.RequestSignals requestSignals(
            AuthRequests.DeviceProfile device, HttpServletRequest request) {
        String hintPlatform = header(request, "Sec-CH-UA-Platform");
        String hintPlatformVersion = header(request, "Sec-CH-UA-Platform-Version");
        String hintModel = header(request, "Sec-CH-UA-Model");
        String hintFormFactors = header(request, "Sec-CH-UA-Form-Factors");
        String hintArchitecture = header(request, "Sec-CH-UA-Arch");
        String hintBitness = header(request, "Sec-CH-UA-Bitness");
        String hintMobile = header(request, "Sec-CH-UA-Mobile");
        String hintUa = header(request, "Sec-CH-UA");
        String hintFullVersionList = header(request, "Sec-CH-UA-Full-Version-List");
        String hintWow64 = header(request, "Sec-CH-UA-WoW64");
        String acceptLanguage = header(request, "Accept-Language");

        boolean compared = false;
        boolean mismatch = false;
        if (hintPlatform != null && device.platform() != null) {
            compared = true;
            mismatch = differs(hintPlatform, device.platform());
        }
        if (hintPlatformVersion != null && device.platformVersion() != null) {
            compared = true;
            mismatch = mismatch || versionDiffers(hintPlatformVersion, device.platformVersion());
        }
        if (hintModel != null && device.model() != null) {
            compared = true;
            mismatch = mismatch || differs(hintModel, device.model());
        }
        if (hintFormFactors != null && device.formFactor() != null) {
            compared = true;
            mismatch = mismatch || factorsDiffer(hintFormFactors, device.formFactor());
        }
        if (hintArchitecture != null && device.architecture() != null) {
            compared = true;
            mismatch = mismatch || differs(hintArchitecture, device.architecture());
        }
        if (hintBitness != null && device.bitness() != null) {
            compared = true;
            mismatch = mismatch || differs(hintBitness, device.bitness());
        }

        return new ApiResponses.RequestSignals(
                clean(hintPlatform, 40),
                clean(hintPlatformVersion, 40),
                clean(hintModel, 80),
                clean(hintFormFactors, 64),
                clean(hintArchitecture, 24),
                clean(hintBitness, 16),
                clean(hintMobile, 8),
                clean(hintUa, 512),
                clean(hintFullVersionList, 512),
                clean(hintWow64, 8),
                clean(acceptLanguage, 160),
                flag(header(request, "DNT")),
                flag(header(request, "Sec-GPC")),
                present(request.getHeader("Via")),
                present(request.getHeader("Forwarded")),
                compared ? mismatch : null
        );
    }

    private static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static Boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static Boolean flag(String value) {
        if ("1".equals(value)) return true;
        if ("0".equals(value)) return false;
        return null;
    }

    private static boolean differs(String serverValue, String clientValue) {
        return !serverValue.equalsIgnoreCase(clientValue);
    }

    private static boolean versionDiffers(String serverValue, String clientValue) {
        String serverMajor = firstSegment(serverValue);
        String clientMajor = firstSegment(clientValue);
        return !serverMajor.isEmpty() && !clientMajor.isEmpty() && !serverMajor.equals(clientMajor);
    }

    private static String firstSegment(String value) {
        int separator = value.indexOf('.');
        return (separator < 0 ? value : value.substring(0, separator)).trim();
    }

    private static boolean factorsDiffer(String serverValue, String clientValue) {
        String server = normalizedFactors(serverValue);
        String client = normalizedFactors(clientValue);
        return !server.isEmpty() && !client.isEmpty() && !server.equals(client);
    }

    private static String normalizedFactors(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .map(part -> part.startsWith("\"") && part.endsWith("\"") && part.length() >= 2
                        ? part.substring(1, part.length() - 1) : part)
                .filter(part -> !part.isEmpty())
                .map(part -> part.toLowerCase(Locale.ROOT))
                .sorted()
                .collect(Collectors.joining(","));
    }

    private static String confidence(String value) {
        String normalized = clean(value, 16);
        if (normalized == null) return null;
        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "high", "medium", "low" -> normalized.toLowerCase(Locale.ROOT);
            default -> null;
        };
    }

    private static String pointerType(String value) {
        String normalized = clean(value, 16);
        if (normalized == null) return null;
        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "fine", "coarse", "none", "unknown" -> normalized.toLowerCase(Locale.ROOT);
            default -> null;
        };
    }

    private static Integer integer(Integer value, int min, int max) {
        if (value == null || value < min || value > max) return null;
        return value;
    }

    private static Double decimal(Double value, double min, double max) {
        if (value == null || !Double.isFinite(value) || value < min || value > max) return null;
        return value;
    }

    private static String deviceType(String value) {
        String type = clean(value, 16);
        if (type == null) return null;
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "desktop", "mobile", "tablet" -> type.toLowerCase(Locale.ROOT);
            default -> "unknown";
        };
    }

    private static String clean(String value, int maxLength) {
        if (value == null) return null;
        String normalized = value.replaceAll("\\p{Cntrl}", "").trim();
        if (normalized.isEmpty()) return null;
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
