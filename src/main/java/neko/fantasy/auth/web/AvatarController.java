package neko.fantasy.auth.web;

import neko.fantasy.auth.dto.ApiResponses;
import neko.fantasy.auth.security.UserPrincipal;
import neko.fantasy.auth.service.AvatarService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
public class AvatarController {

    private static final CacheControl DEFAULT_CACHE =
            CacheControl.maxAge(Duration.ofDays(180)).cachePublic().immutable();
    private static final CacheControl USER_CACHE =
            CacheControl.maxAge(Duration.ZERO).cachePublic().mustRevalidate();

    private final AvatarService avatarService;

    public AvatarController(AvatarService avatarService) {
        this.avatarService = avatarService;
    }

    @PostMapping("/api/auth/avatar")
    public ApiResponses.User upload(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestPart("avatar") MultipartFile avatar
    ) {
        return ApiResponses.User.from(avatarService.upload(principal.id(), avatar));
    }

    @GetMapping("/avatars/default")
    public ResponseEntity<Resource> defaultAvatar(
            @org.springframework.web.bind.annotation.RequestHeader(
                    value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        return response(avatarService.defaultAvatar(), ifNoneMatch);
    }

    @GetMapping("/avatars/{userId}")
    public ResponseEntity<Resource> avatar(
            @PathVariable Long userId,
            @org.springframework.web.bind.annotation.RequestHeader(
                    value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        return response(avatarService.load(userId), ifNoneMatch);
    }

    private static ResponseEntity<Resource> response(AvatarService.StoredAvatar avatar,
                                                     String ifNoneMatch) {
        boolean fresh = ifNoneMatch != null
                && (ifNoneMatch.equals(avatar.etag())
                || ifNoneMatch.contains(avatar.etag()));
        if (fresh) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(avatar.etag())
                    .cacheControl(avatar.immutable() ? DEFAULT_CACHE : USER_CACHE)
                    .build();
        }
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(avatar.mediaType())
                .eTag(avatar.etag())
                .cacheControl(avatar.immutable() ? DEFAULT_CACHE : USER_CACHE);
        if (avatar.lastModified() > 0) {
            builder.lastModified(avatar.lastModified());
        }
        return builder.body(avatar.resource());
    }
}
