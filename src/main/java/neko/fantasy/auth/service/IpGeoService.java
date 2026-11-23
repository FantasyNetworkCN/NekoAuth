package neko.fantasy.auth.service;

import com.github.jarod.qqwry.IPZone;
import com.github.jarod.qqwry.QQWry;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.model.IspResponse;
import com.maxmind.geoip2.record.Location;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import neko.fantasy.auth.security.ClientIpResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

/**
 * 本地 IP 地理定位：优先 MaxMind City/ISP 库，省级运营商 ASN 映射修正国内省份，
 * IPv4 再由 qqwry 补省市。定位结果只用于服务端风控，不返回给用户。
 */
@Service
public class IpGeoService {

    private static final Logger log = LoggerFactory.getLogger(IpGeoService.class);
    private static final int CACHE_SIZE = 8192;
    private static final Pattern REGION_SEPARATOR = Pattern.compile("\\s*[–—-]\\s*");
    private static final Map<String, String> COUNTRY_ZH = Map.ofEntries(
            Map.entry("CN", "中国"),
            Map.entry("HK", "中国香港"),
            Map.entry("MO", "中国澳门"),
            Map.entry("TW", "中国台湾"),
            Map.entry("US", "美国"),
            Map.entry("JP", "日本"),
            Map.entry("KR", "韩国"),
            Map.entry("SG", "新加坡"),
            Map.entry("MY", "马来西亚"),
            Map.entry("TH", "泰国"),
            Map.entry("VN", "越南"),
            Map.entry("PH", "菲律宾"),
            Map.entry("ID", "印度尼西亚"),
            Map.entry("IN", "印度"),
            Map.entry("GB", "英国"),
            Map.entry("DE", "德国"),
            Map.entry("FR", "法国"),
            Map.entry("RU", "俄罗斯"),
            Map.entry("AU", "澳大利亚"),
            Map.entry("CA", "加拿大"),
            Map.entry("BR", "巴西")
    );
    private static final Map<String, double[]> REGION_CENTERS = Map.ofEntries(
            Map.entry("北京", new double[]{39.9042, 116.4074}),
            Map.entry("上海", new double[]{31.2304, 121.4737}),
            Map.entry("天津", new double[]{39.0842, 117.2009}),
            Map.entry("重庆", new double[]{29.5630, 106.5516}),
            Map.entry("河北", new double[]{38.0428, 114.5149}),
            Map.entry("山西", new double[]{37.8706, 112.5489}),
            Map.entry("内蒙古", new double[]{40.8426, 111.7492}),
            Map.entry("辽宁", new double[]{41.8057, 123.4315}),
            Map.entry("吉林", new double[]{43.8171, 125.3235}),
            Map.entry("黑龙江", new double[]{45.8038, 126.5350}),
            Map.entry("江苏", new double[]{32.0603, 118.7969}),
            Map.entry("浙江", new double[]{30.2741, 120.1551}),
            Map.entry("安徽", new double[]{31.8206, 117.2272}),
            Map.entry("福建", new double[]{26.0745, 119.2965}),
            Map.entry("江西", new double[]{28.6820, 115.8579}),
            Map.entry("山东", new double[]{36.6512, 117.1201}),
            Map.entry("河南", new double[]{34.7466, 113.6254}),
            Map.entry("湖北", new double[]{30.5928, 114.3055}),
            Map.entry("湖南", new double[]{28.2282, 112.9388}),
            Map.entry("广东", new double[]{23.1291, 113.2644}),
            Map.entry("广西", new double[]{22.8170, 108.3665}),
            Map.entry("海南", new double[]{20.0440, 110.1999}),
            Map.entry("四川", new double[]{30.5728, 104.0668}),
            Map.entry("贵州", new double[]{26.6470, 106.6302}),
            Map.entry("云南", new double[]{25.0389, 102.7183}),
            Map.entry("西藏", new double[]{29.6520, 91.1721}),
            Map.entry("陕西", new double[]{34.3416, 108.9398}),
            Map.entry("甘肃", new double[]{36.0611, 103.8343}),
            Map.entry("青海", new double[]{36.6171, 101.7782}),
            Map.entry("宁夏", new double[]{38.4872, 106.2309}),
            Map.entry("新疆", new double[]{43.8256, 87.6168}),
            Map.entry("香港", new double[]{22.3193, 114.1694}),
            Map.entry("澳门", new double[]{22.1987, 113.5439}),
            Map.entry("台湾", new double[]{23.6978, 120.9605})
    );

    private final List<Path> directories;
    private final ConcurrentMap<String, IpLocation> cache = new ConcurrentHashMap<>();
    private DatabaseReader cityReader;
    private DatabaseReader asnReader;
    private QQWry qqwry;
    private Map<Long, String> asnRegions = Map.of();

    public IpGeoService(@Value("${auth.geoip.directory:/app/GeoIP}") String configuredDirectory) {
        List<Path> candidates = new ArrayList<>();
        if (configuredDirectory != null && !configuredDirectory.isBlank()) {
            for (String value : configuredDirectory.split(",")) {
                if (!value.isBlank()) candidates.add(Paths.get(value.trim()));
            }
        }
        candidates.add(Paths.get("GeoIP"));
        candidates.add(Paths.get("../NekoMusic/backend/src/main/resources"));
        candidates.add(Paths.get("/app/GeoIP"));
        candidates.add(Paths.get("/usr/share/GeoIP"));
        candidates.add(Paths.get("/usr/local/share/GeoIP"));
        candidates.add(Paths.get("/var/lib/GeoIP"));
        List<Path> expanded = new ArrayList<>();
        for (Path directory : candidates) {
            expanded.add(directory);
            Path nested = directory.resolve("GeoIP");
            if (!expanded.contains(nested)) expanded.add(nested);
        }
        this.directories = List.copyOf(expanded);
    }

    @PostConstruct
    void initialize() {
        cityReader = openMaxMind(
                "GeoIP2-Enterprise.mmdb", "GeoIP2-City.mmdb", "GeoLite2-City.mmdb",
                "GeoIP2-Country.mmdb", "GeoLite2-Country.mmdb");
        asnReader = openMaxMind("GeoIP2-ISP.mmdb", "GeoLite2-ASN.mmdb");
        qqwry = openQqwry();
        asnRegions = loadAsnRegions();
        if (cityReader == null && asnReader == null && qqwry == null) {
            log.warn("未找到本地 IP 地理库；不可达旅行与地区变化检测将降级。"
                    + "请把 GeoLite2-City.mmdb / GeoLite2-ASN.mmdb / qqwry.dat 挂载到 /app/GeoIP");
        } else {
            log.info("IP 风控地理库已加载: city={}, asn={}, qqwry={}, asnRegions={}",
                    cityReader != null, asnReader != null, qqwry != null, asnRegions.size());
        }
    }

    public IpLocation resolve(String rawIp) {
        String ip = ClientIpResolver.normalize(rawIp);
        if (ip.isEmpty()) return IpLocation.unknown();
        IpLocation cached = cache.get(ip);
        if (cached != null) return cached;
        if (ClientIpResolver.isLocalPeer(ip)) {
            return new IpLocation("LOCAL", "本地", "", "", null, null, null, "", "network");
        }

        InetAddress address;
        try {
            address = InetAddress.getByName(ip);
        } catch (Exception exception) {
            return IpLocation.unknown();
        }

        IpLocation maxmind = lookupMaxMind(address);
        Long asn = lookupAsn(address);
        String organization = lookupOrganization(address);
        String asnProvince = asn == null ? "" : asnRegions.getOrDefault(asn, "");
        IpLocation qqwryLocation = IpLocation.unknown();
        if (asnProvince.isEmpty() && qqwry != null && !ip.contains(":")) {
            qqwryLocation = lookupQqwryInstance(ip);
        }
        String countryCode = maxmind.countryCode();
        String countryName = maxmind.countryName();
        if (!asnProvince.isEmpty()) {
            countryCode = "CN";
            countryName = "中国";
        }
        String region = !asnProvince.isEmpty() ? asnProvince
                : firstNonBlank(maxmind.region(), qqwryLocation.region());
        String city = firstNonBlank(maxmind.city(), qqwryLocation.city());
        String resolvedCountry = firstNonBlank(countryName, qqwryLocation.countryName());
        String resolvedCode = firstNonBlank(countryCode, qqwryLocation.countryCode());
        if (resolvedCode.isBlank() && resolvedCountry.isBlank()
                && region.isBlank() && city.isBlank()) {
            return IpLocation.unknown();
        }
        double[] regionCenter = regionCenter(region);
        Double latitude = regionCenter == null ? maxmind.latitude() : regionCenter[0];
        Double longitude = regionCenter == null ? maxmind.longitude() : regionCenter[1];
        IpLocation location = new IpLocation(
                resolvedCode,
                firstNonBlank(localizedCountry(resolvedCode, resolvedCountry), resolvedCountry),
                region,
                city,
                latitude,
                longitude,
                asn,
                organization,
                cityReader == null ? "qqwry/asn" : "maxmind"
        );
        if (cache.size() >= CACHE_SIZE) cache.clear();
        cache.put(ip, location);
        return location;
    }

    /** 两个坐标之间的球面距离，单位 km。 */
    public static double distanceKm(IpLocation first, IpLocation second) {
        if (first == null || second == null || !first.hasCoordinates() || !second.hasCoordinates()) {
            return -1;
        }
        double lat1 = Math.toRadians(first.latitude());
        double lat2 = Math.toRadians(second.latitude());
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(second.longitude() - first.longitude());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return 6371.0088 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private IpLocation lookupMaxMind(InetAddress address) {
        if (cityReader == null) return IpLocation.unknown();
        try {
            Optional<CityResponse> response = cityReader.tryCity(address);
            if (response.isEmpty()) return IpLocation.unknown();
            CityResponse city = response.get();
            var country = city.country();
            var subdivision = city.mostSpecificSubdivision();
            var cityName = city.city();
            Location location = city.location();
            return new IpLocation(
                    safe(country.isoCode()),
                    localized(country.names(), COUNTRY_ZH.getOrDefault(safe(country.isoCode()), "")),
                    localized(subdivision == null ? Map.of() : subdivision.names(), ""),
                    localized(cityName == null ? Map.of() : cityName.names(), ""),
                    location == null ? null : location.latitude(),
                    location == null ? null : location.longitude(),
                    null,
                    "",
                    "maxmind"
            );
        } catch (Exception exception) {
            return IpLocation.unknown();
        }
    }

    private Long lookupAsn(InetAddress address) {
        if (asnReader == null) return null;
        try {
            String type = asnReader.metadata().databaseType();
            if (type != null && type.contains("ISP")) {
                return asnReader.tryIsp(address).map(IspResponse::autonomousSystemNumber).orElse(null);
            }
            return asnReader.tryAsn(address).map(AsnResponse::autonomousSystemNumber).orElse(null);
        } catch (Exception exception) {
            return null;
        }
    }

    private String lookupOrganization(InetAddress address) {
        if (asnReader == null) return "";
        try {
            String type = asnReader.metadata().databaseType();
            if (type != null && type.contains("ISP")) {
                return asnReader.tryIsp(address).map(IspResponse::autonomousSystemOrganization).orElse("");
            }
            return asnReader.tryAsn(address).map(AsnResponse::autonomousSystemOrganization).orElse("");
        } catch (Exception exception) {
            return "";
        }
    }

    private IpLocation lookupQqwryInstance(String ip) {
        if (qqwry == null) return IpLocation.unknown();
        try {
            IPZone zone = qqwry.findIP(ip);
            return parseQqwry(zone == null ? "" : zone.getMainInfo());
        } catch (Exception exception) {
            return IpLocation.unknown();
        }
    }

    private static IpLocation parseQqwry(String mainInfo) {
        String value = mainInfo == null ? "" : mainInfo.trim();
        if (value.isEmpty()) return IpLocation.unknown();
        String[] parts = REGION_SEPARATOR.split(value);
        if (parts.length == 1 && value.startsWith("中国")) parts = value.split("\\s+");
        String country = field(parts, 0);
        if (country.isEmpty()) return IpLocation.unknown();
        if (country.startsWith("中国")) {
            return new IpLocation("CN", "中国", field(parts, 1), field(parts, 2),
                    null, null, null, "", "qqwry");
        }
        return new IpLocation("", country, field(parts, 1), field(parts, 2),
                null, null, null, "", "qqwry");
    }

    private DatabaseReader openMaxMind(String... names) {
        for (Path directory : directories) {
            for (String name : names) {
                Path path = directory.resolve(name);
                if (!Files.isRegularFile(path)) continue;
                try {
                    DatabaseReader reader = new DatabaseReader.Builder(path.toFile()).build();
                    log.info("IP 地理库已加载: {} ({})", path.toAbsolutePath(), reader.metadata().databaseType());
                    return reader;
                } catch (Exception exception) {
                    log.warn("IP 地理库无法读取: {} - {}", path, exception.getMessage());
                }
            }
        }
        return null;
    }

    private QQWry openQqwry() {
        for (Path directory : directories) {
            Path path = directory.resolve("qqwry.dat");
            if (!Files.isRegularFile(path)) continue;
            try {
                QQWry database = new QQWry(Files.readAllBytes(path));
                log.info("qqwry 国内 IP 库已加载: {} ({})",
                        path.toAbsolutePath(), database.getDatabaseVersion());
                return database;
            } catch (Exception exception) {
                log.warn("qqwry 数据库无法读取: {} - {}", path, exception.getMessage());
            }
        }
        return null;
    }

    private Map<Long, String> loadAsnRegions() {
        for (Path directory : directories) {
            Path path = directory.resolve("asn_regions.tsv");
            if (!Files.isRegularFile(path)) continue;
            try (InputStream input = Files.newInputStream(path)) {
                return loadAsnRegions(input);
            } catch (IOException exception) {
                log.warn("ASN 省级映射读取失败: {} - {}", path, exception.getMessage());
            }
        }
        try (InputStream input = IpGeoService.class.getResourceAsStream("/GeoIP/asn_regions.tsv")) {
            return input == null ? Map.of() : loadAsnRegions(input);
        } catch (IOException exception) {
            return Map.of();
        }
    }

    private static Map<Long, String> loadAsnRegions(InputStream input) throws IOException {
        Map<Long, String> regions = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String value = line.trim();
                if (value.isEmpty() || value.startsWith("#")) continue;
                String[] parts = value.split("\\t", -1);
                if (parts.length < 2) continue;
                try {
                    long asn = Long.parseLong(parts[0].trim());
                    String region = parts[1].trim();
                    if (asn > 0 && !region.isEmpty()) regions.put(asn, region);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return Map.copyOf(regions);
    }

    private static String localized(Map<String, String> names, String fallback) {
        if (names == null) return safe(fallback);
        String zh = names.get("zh-CN");
        if (zh != null && !zh.isBlank()) return zh;
        String en = names.get("en");
        return en == null || en.isBlank() ? safe(fallback) : en;
    }

    private static String localizedCountry(String code, String fallback) {
        if (code != null && COUNTRY_ZH.containsKey(code.toUpperCase(Locale.ROOT))) {
            return COUNTRY_ZH.get(code.toUpperCase(Locale.ROOT));
        }
        return fallback;
    }

    private static String field(String[] parts, int index) {
        if (parts == null || index >= parts.length || parts[index] == null) return "";
        return parts[index].trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static double[] regionCenter(String region) {
        if (region == null || region.isBlank()) return null;
        String normalized = region.trim()
                .replaceFirst("(特别行政区|维吾尔自治区|壮族自治区|回族自治区|自治区|省|市)$", "");
        return REGION_CENTERS.get(normalized);
    }

    @PreDestroy
    void close() {
        try {
            if (cityReader != null) cityReader.close();
        } catch (IOException ignored) {
        }
        try {
            if (asnReader != null) asnReader.close();
        } catch (IOException ignored) {
        }
    }

    public record IpLocation(String countryCode, String countryName, String region, String city,
                             Double latitude, Double longitude, Long asn,
                             String organization, String source) {
        public static IpLocation unknown() {
            return new IpLocation("", "", "", "", null, null, null, "", "unknown");
        }

        public boolean hasCoordinates() {
            return latitude != null && longitude != null;
        }

        public boolean hasCountry() {
            return countryCode != null && !countryCode.isBlank();
        }

        public String areaLabel() {
            if (countryName == null || countryName.isBlank()) return "未知";
            if (region != null && !region.isBlank() && !countryName.contains(region)) {
                return countryName + " · " + region;
            }
            return countryName;
        }
    }
}
