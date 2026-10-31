package neko.fantasy.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthRequests {

    private AuthRequests() {
    }

    public record Register(
            @Pattern(regexp = "^$|^[A-Za-z0-9_]{3,32}$", message = "用户名需为 3-32 位字母、数字或下划线")
            String username,

            @NotBlank(message = "邮箱不能为空")
            @Email(message = "邮箱格式不正确")
            @Size(max = 100, message = "邮箱不能超过 100 个字符")
            String email,

            @NotBlank(message = "密码不能为空")
            @Size(min = 6, max = 30, message = "密码长度需为 6-30 个字符")
            String password,

            @Pattern(regexp = "^$|^\\d{6}$", message = "邮箱验证码需为 6 位数字")
            String verificationCode,

            DeviceProfile device
    ) {
    }

    public record RegistrationCode(
            @NotBlank(message = "邮箱不能为空")
            @Email(message = "邮箱格式不正确")
            @Size(max = 100, message = "邮箱不能超过 100 个字符")
            String email,

            @NotBlank(message = "请先获取人机验证码")
            @Size(max = 36, message = "人机验证码无效")
            String captchaId,

            @NotBlank(message = "请输入人机验证码")
            @Pattern(regexp = "^[A-Za-z0-9]{5}$", message = "人机验证码需为 5 位字母或数字")
            String captchaAnswer
    ) {
    }

    public record PasswordResetCode(
            @NotBlank(message = "邮箱不能为空")
            @Email(message = "邮箱格式不正确")
            @Size(max = 100, message = "邮箱不能超过 100 个字符")
            String email,

            @NotBlank(message = "请先获取人机验证码")
            @Size(max = 36, message = "人机验证码无效")
            String captchaId,

            @NotBlank(message = "请输入人机验证码")
            @Pattern(regexp = "^[A-Za-z0-9]{5}$", message = "人机验证码需为 5 位字母或数字")
            String captchaAnswer
    ) {
    }

    public record ResetPassword(
            @NotBlank(message = "邮箱不能为空")
            @Email(message = "邮箱格式不正确")
            @Size(max = 100, message = "邮箱不能超过 100 个字符")
            String email,

            @NotBlank(message = "请输入邮箱验证码")
            @Pattern(regexp = "^\\d{6}$", message = "邮箱验证码需为 6 位数字")
            String code,

            @NotBlank(message = "请输入新密码")
            @Size(min = 6, max = 30, message = "新密码长度需为 6-30 个字符")
            String newPassword
    ) {
    }

    public record Login(
            @NotBlank(message = "请输入用户名或邮箱")
            @Size(max = 254, message = "用户名或邮箱过长")
            String identifier,

            @NotBlank(message = "请输入密码")
            @Size(max = 128, message = "密码过长")
            String password,

            DeviceProfile device
    ) {
    }

    /** 浏览器尽力提供的设备画像；仅用于展示与风险提示，不作为身份认证凭证。 */
    public record DeviceProfile(
            String type,
            String platform,
            String platformVersion,
            String architecture,
            String model,
            String browser,
            String browserVersion,
            String screen,
            String timezone,
            Boolean touch,
            String source
    ) {
    }

    public record ChangePassword(
            @NotBlank(message = "请输入当前密码")
            @Size(max = 128, message = "当前密码过长")
            String currentPassword,

            @NotBlank(message = "请输入新密码")
            @Size(min = 6, max = 30, message = "新密码长度需为 6-30 个字符")
            String newPassword
    ) {
    }

    public record AdminUpdateUser(Boolean enabled, neko.fantasy.auth.domain.Role role) {
    }
}
