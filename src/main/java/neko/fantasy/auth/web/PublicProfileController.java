package neko.fantasy.auth.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import neko.fantasy.auth.domain.User;
import neko.fantasy.auth.repository.UserRepository;
import neko.fantasy.auth.service.AvatarService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 第一方业务展示所需的公开账号摘要，不返回邮箱、角色或任何令牌。 */
@RestController
@RequestMapping("/api/public/users")
public class PublicProfileController {

    private final UserRepository userRepository;

    public PublicProfileController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Profile> get(@PathVariable long id) {
        return userRepository.findById(id)
                .filter(User::isEnabled)
                .map(user -> ResponseEntity.ok()
                        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=60")
                        .body(Profile.from(user)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/lookup")
    public List<Profile> lookup(@Valid @RequestBody Lookup request) {
        return userRepository.findAllById(request.ids()).stream()
                .filter(User::isEnabled)
                .map(Profile::from)
                .toList();
    }

    @PostMapping("/search")
    public List<Profile> search(@Valid @RequestBody Search request) {
        return userRepository.search(request.query().trim(), PageRequest.of(0, 20))
                .stream()
                .filter(User::isEnabled)
                .map(Profile::from)
                .toList();
    }

    public record Profile(long id, String username, String nickname, String avatarUrl) {
        static Profile from(User user) {
            return new Profile(
                    user.getId(),
                    user.getUsername(),
                    user.publicName(),
                    AvatarService.avatarUrl(user.getId()));
        }
    }

    public record Lookup(
            @NotEmpty(message = "至少需要一个用户 ID")
            @Size(max = 200, message = "单次最多查询 200 个用户")
            List<@NotNull(message = "用户 ID 不能为空")
                 @Positive(message = "用户 ID 无效") Long> ids
    ) {
    }

    public record Search(
            @NotBlank(message = "请输入搜索关键词")
            @Size(max = 100, message = "搜索关键词过长")
            String query
    ) {
    }
}
