package neko.fantasy.auth.oauth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import neko.fantasy.auth.config.OAuthProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class OAuthKeyService {

    private final OAuthProperties properties;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String keyId;
    private final String issuer;

    public OAuthKeyService(OAuthProperties properties) {
        this.properties = properties;
        this.issuer = normalizeIssuer(properties.issuer());
        KeyPair keyPair = loadOrCreateKeyPair(Path.of(properties.keyFile()).toAbsolutePath().normalize());
        this.privateKey = keyPair.getPrivate();
        this.publicKey = keyPair.getPublic();
        this.keyId = hashKey(publicKey.getEncoded()).substring(0, 16);
    }

    public String getIssuer() {
        return issuer;
    }

    public String issue(Map<String, Object> claims, String subject, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .claims(claims)
                .header().keyId(keyId).and()
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Claims parseForLogout(String token) throws JwtException {
        try {
            return parse(token);
        } catch (ExpiredJwtException exception) {
            Claims claims = exception.getClaims();
            if (!issuer.equals(claims.getIssuer())) {
                throw new JwtException("Invalid token issuer", exception);
            }
            return claims;
        }
    }

    public Map<String, Object> jwks() {
        RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;
        Map<String, Object> key = new LinkedHashMap<>();
        key.put("kty", "RSA");
        key.put("use", "sig");
        key.put("alg", "RS256");
        key.put("kid", keyId);
        key.put("n", unsignedBase64(rsaPublicKey.getModulus()));
        key.put("e", unsignedBase64(rsaPublicKey.getPublicExponent()));
        return Map.of("keys", java.util.List.of(key));
    }

    public String getKeyId() {
        return keyId;
    }

    private static KeyPair loadOrCreateKeyPair(Path file) {
        try {
            if (Files.exists(file)) {
                String pem = Files.readString(file, StandardCharsets.US_ASCII);
                String privateText = pemSection(pem, "PRIVATE KEY");
                String publicText = pemSection(pem, "PUBLIC KEY");
                byte[] privateBytes = Base64.getDecoder().decode(privateText);
                byte[] publicBytes = Base64.getDecoder().decode(publicText);
                KeyFactory factory = KeyFactory.getInstance("RSA");
                return new KeyPair(
                        factory.generatePublic(new X509EncodedKeySpec(publicBytes)),
                        factory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes)));
            }

            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            Files.createDirectories(file.getParent());
            String pem = "-----BEGIN PRIVATE KEY-----\n"
                    + wrap(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()))
                    + "\n-----END PRIVATE KEY-----\n"
                    + "-----BEGIN PUBLIC KEY-----\n"
                    + wrap(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()))
                    + "\n-----END PUBLIC KEY-----\n";
            Files.writeString(file, pem, StandardCharsets.US_ASCII,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // POSIX permissions are not available on every platform.
            }
            return keyPair;
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalStateException("无法加载或创建 OAuth 签名密钥：" + file, exception);
        }
    }

    private static String pemSection(String pem, String label) {
        String begin = "-----BEGIN " + label + "-----";
        String end = "-----END " + label + "-----";
        int start = pem.indexOf(begin);
        int finish = pem.indexOf(end);
        if (start < 0 || finish < 0) {
            throw new IllegalStateException("OAuth 签名密钥文件缺少 " + label + " 段");
        }
        return pem.substring(start + begin.length(), finish).replaceAll("\\s+", "");
    }

    private static String wrap(String value) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index += 64) {
            result.append(value, index, Math.min(index + 64, value.length())).append('\n');
        }
        return result.toString().stripTrailing();
    }

    private static String unsignedBase64(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] unsigned = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, unsigned, 0, unsigned.length);
            bytes = unsigned;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashKey(byte[] encoded) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String normalizeIssuer(String issuer) {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("oauth.issuer 不能为空");
        }
        String normalized = issuer.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
