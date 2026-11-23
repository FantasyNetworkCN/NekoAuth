package neko.fantasy.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 可信客户端 IP 解析。只有 socket 对端是本机或内网时，才信任反向代理 / CDN 写入的转发头。
 * 同一请求内结果缓存，确保限流、设备画像和审计使用同一个地址。
 */
@Component
public class ClientIpResolver {

    private static final String REQUEST_ATTRIBUTE = ClientIpResolver.class.getName() + ".clientIp";
    private static final String AUTO = "auto";
    private static final String DIRECT = "direct";
    private static final String XFF = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";
    private static final List<String> AUTO_CDN_HEADERS = List.of(
            "CF-Connecting-IP",
            "True-Client-IP",
            "Ali-CDN-Real-IP",
            "Ali-Real-Client-IP",
            "X-Edge-Real-IP",
            "Fastly-Client-IP",
            "X-Client-IP",
            "X-Azure-ClientIP",
            "CloudFront-Viewer-Address"
    );

    private final String trustedHeader;
    private final List<String> extraHeaders;

    public ClientIpResolver(
            @Value("${auth.client-ip.trusted-header:auto}") String trustedHeader,
            @Value("${auth.client-ip.extra-headers:}") String extraHeaders
    ) {
        this.trustedHeader = trustedHeader == null ? AUTO : trustedHeader.trim();
        this.extraHeaders = extraHeaders == null || extraHeaders.isBlank()
                ? List.of()
                : java.util.Arrays.stream(extraHeaders.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) return "";
        Object cached = request.getAttribute(REQUEST_ATTRIBUTE);
        if (cached instanceof String value) return value;
        String resolved = resolve(request.getRemoteAddr(), trustedHeader, request::getHeader, extraHeaders);
        request.setAttribute(REQUEST_ATTRIBUTE, resolved);
        return resolved;
    }

    static String resolve(String peerAddress, String trustedHeader, Function<String, String> headers) {
        return resolve(peerAddress, trustedHeader, headers, List.of());
    }

    static String resolve(String peerAddress, String trustedHeader, Function<String, String> headers,
                          List<String> extraHeaders) {
        Function<String, String> source = headers == null ? name -> null : headers;
        String peer = normalize(peerAddress);
        String header = trustedHeader == null ? "" : trustedHeader.trim();
        if (header.isEmpty() || DIRECT.equalsIgnoreCase(header)) return peer;
        if (AUTO.equalsIgnoreCase(header)) return autoDetect(peer, source, extraHeaders);

        String raw = source.apply(header);
        String ip = XFF.equalsIgnoreCase(header) ? rightmostEntry(raw) : singleEntry(raw);
        return normalize(ip.isEmpty() ? peer : ip);
    }

    private static String autoDetect(String peer, Function<String, String> headers, List<String> extraHeaders) {
        if (!isLocalPeer(peer)) return peer;
        if (extraHeaders != null) {
            for (String name : extraHeaders) {
                if (name == null || name.isBlank()) continue;
                String ip = singleEntry(headers.apply(name.trim()));
                if (!ip.isEmpty()) return ip;
            }
        }

        List<String> candidates = new ArrayList<>();
        for (String name : AUTO_CDN_HEADERS) {
            String ip = singleEntry(headers.apply(name));
            if (!ip.isEmpty() && !candidates.contains(ip)) candidates.add(ip);
        }
        String rawXff = headers.apply(XFF);
        String real = singleEntry(headers.apply(X_REAL_IP));
        String chain = chainEntry(rawXff, real);
        for (String candidate : candidates) {
            if (candidate.equals(real) || candidate.equals(chain)) return candidate;
        }
        if (witnessFromUpstream(rawXff, real, chain)) return chain;
        if (candidates.size() == 1) return candidates.getFirst();
        if (!real.isEmpty()) return real;
        return chain.isEmpty() ? peer : chain;
    }

    static String chainEntry(String rawXff, String upstreamRealIp) {
        if (rawXff == null || rawXff.isBlank()) return "";
        List<String> chain = new ArrayList<>();
        for (String part : rawXff.split(",")) {
            String value = normalize(part);
            if (!value.isEmpty()) chain.add(value);
        }
        String hop = normalize(upstreamRealIp);
        if (!chain.isEmpty() && !hop.isEmpty() && hop.equals(chain.getLast())) {
            chain.removeLast();
        }
        for (int index = chain.size() - 1; index >= 0; index--) {
            if (!isLoopbackOrAny(chain.get(index))) return chain.get(index);
        }
        return "";
    }

    private static boolean witnessFromUpstream(String rawXff, String realIp, String chainIp) {
        return !realIp.isEmpty() && !chainIp.isEmpty() && xffContains(rawXff, realIp);
    }

    private static boolean xffContains(String rawXff, String ip) {
        if (rawXff == null || rawXff.isBlank()) return false;
        for (String part : rawXff.split(",")) {
            if (normalize(part).equals(ip)) return true;
        }
        return false;
    }

    public static boolean isLocalPeer(String ip) {
        if (ip == null || ip.isBlank()) return true;
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                    || address.isSiteLocalAddress() || address.isLinkLocalAddress()) return true;
            byte[] bytes = address.getAddress();
            return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
        } catch (Exception exception) {
            return true;
        }
    }

    private static boolean isLoopbackOrAny(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isLoopbackAddress() || address.isAnyLocalAddress();
        } catch (Exception exception) {
            return true;
        }
    }

    private static String rightmostEntry(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String[] parts = raw.split(",");
        return parts[parts.length - 1].trim();
    }

    private static String singleEntry(String raw) {
        if (raw == null || raw.isBlank()) return "";
        int comma = raw.indexOf(',');
        return normalize(comma < 0 ? raw : raw.substring(0, comma));
    }

    public static String normalize(String raw) {
        if (raw == null) return "";
        String ip = raw.trim();
        if (ip.isEmpty()) return "";
        int comma = ip.indexOf(',');
        if (comma >= 0) ip = ip.substring(0, comma).trim();
        if (ip.startsWith("[")) {
            int end = ip.indexOf(']');
            if (end > 0) ip = ip.substring(1, end);
        }
        if (ip.startsWith("::ffff:")) ip = ip.substring("::ffff:".length());
        if (ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) return "";
        int firstColon = ip.indexOf(':');
        if (firstColon > 0 && ip.indexOf('.') > 0 && firstColon == ip.lastIndexOf(':')
                && ip.substring(firstColon + 1).chars().allMatch(Character::isDigit)) {
            ip = ip.substring(0, firstColon);
        }
        return isPlausible(ip) ? ip : "";
    }

    private static boolean isPlausible(String ip) {
        if (ip.contains(":")) return true;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        for (String part : parts) {
            try {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) return false;
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return true;
    }
}
