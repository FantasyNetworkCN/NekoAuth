package neko.fantasy.auth.service;

import neko.fantasy.auth.config.AvatarProperties;
import neko.fantasy.auth.domain.User;
import neko.fantasy.auth.exception.ApiException;
import neko.fantasy.auth.repository.UserRepository;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AvatarService {

    private static final String DEFAULT_AVATAR = "site/favicon.ico";
    private static final long DEFAULT_MAX_BYTES = 10L * 1024L * 1024L;

    private final UserRepository userRepository;
    private final Path avatarDirectory;
    private final long maxBytes;
    private volatile StoredAvatar cachedDefaultAvatar;

    public AvatarService(UserRepository userRepository, AvatarProperties properties) {
        this.userRepository = userRepository;
        String configured = properties == null ? null : properties.directory();
        this.avatarDirectory = Path.of(StringUtils.hasText(configured) ? configured : "./data/avatars")
                .toAbsolutePath().normalize();
        long configuredMax = properties == null ? 0 : properties.maxBytes();
        this.maxBytes = configuredMax > 0 ? configuredMax : DEFAULT_MAX_BYTES;
    }

    public static String avatarUrl(long userId) {
        return "/avatars/" + userId;
    }

    @Transactional
    public User upload(Long userId, MultipartFile file) {
        User user = userRepository.findById(userId)
                .filter(User::isEnabled)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "用户不存在或已停用"));
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("AVATAR_REQUIRED", "请选择头像图片");
        }
        if (file.getSize() > maxBytes) {
            throw ApiException.badRequest("AVATAR_TOO_LARGE", "头像不能超过 10MiB");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw ApiException.badRequest("AVATAR_READ_FAILED", "无法读取头像图片");
        }
        if (bytes.length == 0 || bytes.length > maxBytes) {
            throw ApiException.badRequest("AVATAR_TOO_LARGE", "头像不能超过 10MiB");
        }
        ImageType type = detectImageType(bytes);
        if (type == null) {
            throw ApiException.badRequest("AVATAR_INVALID", "头像仅支持 PNG、JPEG、GIF、WebP 或 BMP");
        }

        String fileName = userId + "-" + UUID.randomUUID() + "." + type.extension();
        Path newFile = safeResolve(fileName);
        Path temporary = null;
        try {
            Files.createDirectories(avatarDirectory);
            temporary = Files.createTempFile(avatarDirectory, ".avatar-", ".tmp");
            Files.write(temporary, bytes);
            Files.move(temporary, newFile, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            deleteQuietly(temporary);
            deleteQuietly(newFile);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "AVATAR_STORAGE_FAILED", "头像保存失败");
        }

        String previous = user.getAvatarFile();
        user.setAvatarFile(fileName);
        try {
            User saved = userRepository.saveAndFlush(user);
            deleteAfterCommit(previous, newFile);
            return saved;
        } catch (RuntimeException exception) {
            deleteQuietly(newFile);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public StoredAvatar defaultAvatar() {
        StoredAvatar cached = cachedDefaultAvatar;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (cachedDefaultAvatar == null) {
                try (var input = getClass().getClassLoader().getResourceAsStream(DEFAULT_AVATAR)) {
                    if (input == null) {
                        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                                "AVATAR_DEFAULT_MISSING", "默认头像资源缺失");
                    }
                    byte[] bytes = input.readAllBytes();
                    cachedDefaultAvatar = new StoredAvatar(
                            new ByteArrayResource(bytes),
                            MediaType.parseMediaType("image/x-icon"),
                            "\"" + HexFormat.of().formatHex(sha256(bytes)).substring(0, 24) + "\"",
                            -1,
                            false);
                } catch (IOException exception) {
                    throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "AVATAR_DEFAULT_MISSING", "默认头像资源读取失败");
                }
            }
            return cachedDefaultAvatar;
        }
    }

    @Transactional(readOnly = true)
    public StoredAvatar load(Long userId) {
        User user = userId == null || userId <= 0
                ? null
                : userRepository.findById(userId).orElse(null);
        if (user == null || !StringUtils.hasText(user.getAvatarFile())) {
            return defaultAvatar();
        }
        Path file;
        try {
            file = safeResolve(user.getAvatarFile());
        } catch (RuntimeException exception) {
            return defaultAvatar();
        }
        if (!Files.isRegularFile(file)) {
            return defaultAvatar();
        }
        try {
            long size = Files.size(file);
            long modified = Files.getLastModifiedTime(file).toMillis();
            String etag = etag(userId, size, modified);
            return new StoredAvatar(new FileSystemResource(file), mediaType(user.getAvatarFile()),
                    etag, modified, false);
        } catch (IOException exception) {
            return defaultAvatar();
        }
    }

    private Path safeResolve(String fileName) {
        Path resolved = avatarDirectory.resolve(fileName).normalize();
        if (!resolved.startsWith(avatarDirectory)) {
            throw ApiException.badRequest("AVATAR_INVALID", "头像文件名无效");
        }
        return resolved;
    }

    private void deleteAfterCommit(String previousFileName, Path newFile) {
        if (!StringUtils.hasText(previousFileName) || previousFileName.equals(newFile.getFileName().toString())) {
            return;
        }
        Path previous;
        try {
            previous = safeResolve(previousFileName);
        } catch (RuntimeException exception) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteQuietly(previous);
                }
            });
        } else {
            deleteQuietly(previous);
        }
    }

    private static String etag(Long userId, long size, long modified) {
        try {
            byte[] input = (userId + ":" + size + ":" + modified).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input))
                    .substring(0, 24) + "\"";
        } catch (NoSuchAlgorithmException exception) {
            return "\"" + userId + "-" + size + "-" + modified + "\"";
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static MediaType mediaType(String fileName) {
        String lower = fileName.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (lower.endsWith(".bmp")) return MediaType.parseMediaType("image/bmp");
        return MediaType.IMAGE_PNG;
    }

    private static ImageType detectImageType(byte[] bytes) {
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47
                && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) {
            return ImageType.PNG;
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return ImageType.JPEG;
        }
        if (bytes.length >= 6) {
            String signature = new String(bytes, 0, 6, java.nio.charset.StandardCharsets.US_ASCII);
            if ("GIF87a".equals(signature) || "GIF89a".equals(signature)) {
                return ImageType.GIF;
            }
        }
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return ImageType.WEBP;
        }
        if (bytes.length >= 2 && bytes[0] == 'B' && bytes[1] == 'M') {
            return ImageType.BMP;
        }
        return null;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    public record StoredAvatar(Resource resource, MediaType mediaType, String etag,
                               long lastModified, boolean immutable) {
    }

    private enum ImageType {
        PNG("png"),
        JPEG("jpg"),
        GIF("gif"),
        WEBP("webp"),
        BMP("bmp");

        private final String extension;

        ImageType(String extension) {
            this.extension = extension;
        }

        String extension() {
            return extension;
        }
    }
}
