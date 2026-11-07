package neko.fantasy.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * NekoMusic 等可信第一方服务使用的账号桥接请求。
 *
 * <p>这些请求只在服务端之间传输，不面向浏览器客户端。</p>
 */
public final class InternalAccountRequests {

    private InternalAccountRequests() {
    }

    public record AdminCreate(
            @Size(max = 32, message = "昵称不能超过 32 个字符")
            @Pattern(regexp = "^$|^[^\\p{Cntrl}]{1,32}$", message = "昵称不能包含控制字符")
            String nickname,

            @NotBlank(message = "邮箱不能为空")
            @Email(message = "邮箱格式不正确")
            @Size(max = 254, message = "邮箱过长")
            String email,

            @NotBlank(message = "密码不能为空")
            @Size(min = 6, max = 30, message = "密码长度需为 6-30 个字符")
            String password
    ) {
    }

    public record UpdateNickname(
            @NotBlank(message = "邮箱不能为空")
            @Email(message = "邮箱格式不正确")
            @Size(max = 254, message = "邮箱过长")
            String email,

            @NotBlank(message = "请输入昵称")
            @Size(max = 32, message = "昵称不能超过 32 个字符")
            @Pattern(regexp = "^[^\\p{Cntrl}]{1,32}$", message = "昵称不能包含控制字符")
            String nickname
    ) {
    }

    public record Lookup(
            @NotEmpty(message = "至少需要一个 NekoAuth 用户 ID")
            @Size(max = 200, message = "单次最多查询 200 个用户")
            List<@NotNull(message = "NekoAuth 用户 ID 不能为空")
                 @Positive(message = "NekoAuth 用户 ID 无效") Long> authUserIds
    ) {
    }

    public record Search(
            @NotBlank(message = "请输入搜索关键词")
            @Size(max = 100, message = "搜索关键词过长")
            String query
    ) {
    }

    public record SsoSession(
            @NotBlank(message = "共享登录会话不能为空")
            @Pattern(regexp = "^[A-Za-z0-9-]{16,64}$", message = "共享登录会话格式不正确")
            String ssoSession
    ) {
    }

    public record SetPassword(
            @NotBlank(message = "邮箱不能为空")
            @Email(message = "邮箱格式不正确")
            @Size(max = 254, message = "邮箱过长")
            String email,

            @NotBlank(message = "请输入新密码")
            @Size(min = 6, max = 30, message = "新密码长度需为 6-30 个字符")
            String newPassword
    ) {
    }

    public record ExchangeToken(
            @NotBlank(message = "缺少 NekoAuth 访问令牌")
            @Size(max = 8192, message = "访问令牌过长")
            String accessToken
    ) {
    }
}
