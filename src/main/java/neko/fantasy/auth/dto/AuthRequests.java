package neko.fantasy.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthRequests {

    private AuthRequests() {
    }

    public record Register(
            @NotBlank(message = "用户名不能为空")
            @Pattern(regexp = "^[A-Za-z0-9_]{3,32}$", message = "用户名需为 3-32 位字母、数字或下划线")
            String username,

            @NotBlank(message = "邮箱不能为空")
            @Email(message = "邮箱格式不正确")
            @Size(max = 254, message = "邮箱过长")
            String email,

            @Size(max = 64, message = "昵称不能超过 64 个字符")
            String displayName,

            @NotBlank(message = "密码不能为空")
            @Size(min = 8, max = 64, message = "密码长度需为 8-64 个字符")
            String password
    ) {
    }

    public record Login(
            @NotBlank(message = "请输入用户名或邮箱")
            @Size(max = 254, message = "用户名或邮箱过长")
            String identifier,

            @NotBlank(message = "请输入密码")
            @Size(max = 128, message = "密码过长")
            String password
    ) {
    }

    public record UpdateProfile(
            @NotBlank(message = "昵称不能为空")
            @Size(max = 64, message = "昵称不能超过 64 个字符")
            String displayName
    ) {
    }

    public record ChangePassword(
            @NotBlank(message = "请输入当前密码")
            @Size(max = 128, message = "当前密码过长")
            String currentPassword,

            @NotBlank(message = "请输入新密码")
            @Size(min = 8, max = 64, message = "新密码长度需为 8-64 个字符")
            String newPassword
    ) {
    }

    public record AdminUpdateUser(Boolean enabled, neko.fantasy.auth.domain.Role role) {
    }
}
