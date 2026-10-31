package neko.fantasy.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * NekoMusic 等可信第一方服务使用的账号桥接请求。
 *
 * <p>这些请求只在服务端之间传输，不面向浏览器客户端。</p>
 */
public final class InternalAccountRequests {

    private InternalAccountRequests() {
    }

    public record Login(
            @NotBlank(message = "请输入邮箱")
            @Email(message = "邮箱格式不正确")
            @Size(max = 254, message = "邮箱过长")
            String email,

            @NotBlank(message = "请输入密码")
            @Size(max = 128, message = "密码过长")
            String password
    ) {
    }

    public record EmailRequest(
            @NotBlank(message = "邮箱不能为空")
            @Email(message = "邮箱格式不正确")
            @Size(max = 254, message = "邮箱过长")
            String email
    ) {
    }

    public record Register(
            @NotBlank(message = "邮箱不能为空")
            @Email(message = "邮箱格式不正确")
            @Size(max = 254, message = "邮箱过长")
            String email,

            @NotBlank(message = "密码不能为空")
            @Size(min = 6, max = 30, message = "密码长度需为 6-30 个字符")
            String password,

            @Pattern(regexp = "^\\d{6}$", message = "邮箱验证码需为 6 位数字")
            String verificationCode
    ) {
    }

    public record AdminCreate(
            @NotBlank(message = "邮箱不能为空")
            @Email(message = "邮箱格式不正确")
            @Size(max = 254, message = "邮箱过长")
            String email,

            @NotBlank(message = "密码不能为空")
            @Size(min = 6, max = 30, message = "密码长度需为 6-30 个字符")
            String password
    ) {
    }

    public record ResetPassword(
            @NotBlank(message = "邮箱不能为空")
            @Email(message = "邮箱格式不正确")
            @Size(max = 254, message = "邮箱过长")
            String email,

            @NotBlank(message = "请输入邮箱验证码")
            @Pattern(regexp = "^\\d{6}$", message = "邮箱验证码需为 6 位数字")
            String code,

            @NotBlank(message = "请输入新密码")
            @Size(min = 6, max = 30, message = "新密码长度需为 6-30 个字符")
            String newPassword
    ) {
    }

    public record ChangePassword(
            @NotBlank(message = "邮箱不能为空")
            @Email(message = "邮箱格式不正确")
            @Size(max = 254, message = "邮箱过长")
            String email,

            @NotBlank(message = "请输入当前密码")
            @Size(max = 128, message = "当前密码过长")
            String currentPassword,

            @NotBlank(message = "请输入新密码")
            @Size(min = 6, max = 30, message = "新密码长度需为 6-30 个字符")
            String newPassword
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
