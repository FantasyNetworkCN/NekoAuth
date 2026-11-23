package neko.fantasy.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import neko.fantasy.auth.service.SmsRegistrationService;

public final class AuthRequests {

    private AuthRequests() {
    }

    public record Register(
            @Pattern(regexp = "^$|^[A-Za-z0-9_]{3,32}$", message = "用户名需为 3-32 位字母、数字或下划线")
            String username,

            @Size(max = 32, message = "昵称不能超过 32 个字符")
            @Pattern(regexp = "^$|^[^\\p{Cntrl}]{1,32}$", message = "昵称不能包含控制字符")
            String nickname,

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

    public record PhoneRegistrationCode(
            @NotBlank(message = "请输入手机号")
            @Pattern(regexp = SmsRegistrationService.PHONE_PATTERN, message = "请输入有效的中国大陆手机号")
            String phone,

            @NotBlank(message = "请先获取人机验证码")
            @Size(max = 36, message = "人机验证码无效")
            String captchaId,

            @NotBlank(message = "请输入人机验证码")
            @Pattern(regexp = "^[A-Za-z0-9]{5}$", message = "人机验证码需为 5 位字母或数字")
            String captchaAnswer
    ) {
    }

    public record PhoneRegister(
            @NotBlank(message = "请输入手机号")
            @Pattern(regexp = SmsRegistrationService.PHONE_PATTERN, message = "请输入有效的中国大陆手机号")
            String phone,

            @Size(max = 32, message = "昵称不能超过 32 个字符")
            @Pattern(regexp = "^$|^[^\\p{Cntrl}]{1,32}$", message = "昵称不能包含控制字符")
            String nickname,

            @NotBlank(message = "密码不能为空")
            @Size(min = 6, max = 30, message = "密码长度需为 6-30 个字符")
            String password,

            @NotBlank(message = "请输入短信验证码")
            @Pattern(regexp = "^[0-9]{6}$", message = "短信验证码需为 6 位数字")
            String verificationCode,

            DeviceProfile device
    ) {
    }

    public record BindEmail(
            @NotBlank(message = "邮箱不能为空")
            @Email(message = "邮箱格式不正确")
            @Size(max = 100, message = "邮箱不能超过 100 个字符")
            String email,

            @NotBlank(message = "请输入邮箱验证码")
            @Pattern(regexp = "^[0-9]{6}$", message = "邮箱验证码需为 6 位数字")
            String verificationCode,

            @NotBlank(message = "请输入当前密码")
            @Size(max = 128, message = "当前密码过长")
            String currentPassword
    ) {
    }

    public record BindPhone(
            @NotBlank(message = "请输入手机号")
            @Pattern(regexp = SmsRegistrationService.PHONE_PATTERN, message = "请输入有效的中国大陆手机号")
            String phone,

            @NotBlank(message = "请输入短信验证码")
            @Pattern(regexp = "^[0-9]{6}$", message = "短信验证码需为 6 位数字")
            String verificationCode,

            @NotBlank(message = "请输入当前密码")
            @Size(max = 128, message = "当前密码过长")
            String currentPassword
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

    public record ResetPhonePassword(
            @NotBlank(message = "请输入手机号")
            @Pattern(regexp = SmsRegistrationService.PHONE_PATTERN, message = "请输入有效的中国大陆手机号")
            String phone,

            @NotBlank(message = "请输入短信验证码")
            @Pattern(regexp = "^[0-9]{6}$", message = "短信验证码需为 6 位数字")
            String code,

            @NotBlank(message = "请输入新密码")
            @Size(min = 6, max = 30, message = "新密码长度需为 6-30 个字符")
            String newPassword
    ) {
    }

    public record Login(
            @NotBlank(message = "请输入用户名、邮箱或手机号")
            @Size(max = 254, message = "登录账号过长")
            String identifier,

            @NotBlank(message = "请输入密码")
            @Size(max = 128, message = "密码过长")
            String password,

            DeviceProfile device
    ) {
    }

    public record LoginStepUpVerify(
            @NotBlank(message = "登录验证已失效，请重新登录")
            @Size(max = 36, message = "登录验证已失效，请重新登录")
            String challengeId,

            @NotBlank(message = "请输入验证码")
            @Pattern(regexp = "^[0-9]{6}$", message = "验证码需为 6 位数字")
            String code
    ) {
    }

    public record LoginStepUpResend(
            @NotBlank(message = "登录验证已失效，请重新登录")
            @Size(max = 36, message = "登录验证已失效，请重新登录")
            String challengeId,

            @Pattern(regexp = "^(email|sms)$", message = "不支持的验证方式")
            String method
    ) {
    }

    /** 浏览器尽力提供的设备画像；仅用于服务端会话展示与风险判断，不作为身份认证凭证。 */
    public record DeviceProfile(
            String type,
            String typeConfidence,
            String formFactor,
            String platform,
            String platformVersion,
            String platformConfidence,
            String architecture,
            String architectureClaim,
            String bitness,
            String model,
            String modelSource,
            String browser,
            String browserVersion,
            String screen,
            String screenCss,
            String screenAvailable,
            String viewport,
            String orientation,
            Integer colorDepth,
            Double pixelRatio,
            Integer refreshRate,
            Boolean multipleScreens,
            String displayMode,
            String timezone,
            String languages,
            Boolean touch,
            Integer maxTouchPoints,
            String pointer,
            Boolean hover,
            Double deviceMemory,
            Integer hardwareConcurrency,
            String gpuVendor,
            String gpuRenderer,
            Double batteryLevel,
            Boolean batteryCharging,
            String connectionType,
            String effectiveType,
            Double downlinkMbps,
            Integer rttMs,
            Boolean saveData,
            Boolean webdriver,
            String typeEvidence,
            String platformEvidence,
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

    public record UpdateProfile(
            @NotBlank(message = "请输入昵称")
            @Size(max = 32, message = "昵称不能超过 32 个字符")
            @Pattern(regexp = "^[^\\p{Cntrl}]{1,32}$", message = "昵称不能包含控制字符")
            String nickname
    ) {
    }

    public record AdminUpdateUser(Boolean enabled, neko.fantasy.auth.domain.Role role) {
    }
}
