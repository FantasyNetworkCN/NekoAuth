package neko.fantasy.auth.settings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class SettingsRequests {

    private SettingsRequests() {
    }

    public record Update(
            @NotBlank(message = "服务名称不能为空")
            @Size(max = 80, message = "服务名称不能超过 80 个字符")
            String serviceName,

            boolean registrationEnabled,

            @NotNull(message = "邮箱域名白名单不能为空")
            @Size(min = 1, max = 50, message = "邮箱域名白名单需包含 1-50 个域名")
            List<@NotBlank(message = "邮箱域名不能为空")
                    @Size(max = 253, message = "邮箱域名不能超过 253 个字符") String> emailDomainWhitelist,

            @Valid
            @NotNull(message = "SMTP 设置不能为空")
            Smtp smtp
    ) {
    }

    public record Smtp(
            boolean enabled,

            @Size(max = 255, message = "SMTP 主机不能超过 255 个字符")
            String host,

            @Min(value = 1, message = "SMTP 端口需在 1-65535 之间")
            @Max(value = 65535, message = "SMTP 端口需在 1-65535 之间")
            int port,

            @Size(max = 255, message = "SMTP 用户名不能超过 255 个字符")
            String username,

            @Size(max = 1024, message = "SMTP 密码过长")
            String password,

            boolean clearPassword,

            @NotNull(message = "请选择 SMTP 加密方式")
            MailSecurity security,

            boolean authEnabled,

            @Email(message = "发件地址格式不正确")
            @Size(max = 254, message = "发件地址不能超过 254 个字符")
            String fromEmail,

            @Size(max = 120, message = "发件人名称不能超过 120 个字符")
            String fromName,

            @Min(value = 1000, message = "连接超时不能小于 1000 毫秒")
            @Max(value = 120000, message = "连接超时不能超过 120000 毫秒")
            int connectionTimeoutMs,

            @Min(value = 1000, message = "读取超时不能小于 1000 毫秒")
            @Max(value = 120000, message = "读取超时不能超过 120000 毫秒")
            int readTimeoutMs,

            @Min(value = 1000, message = "写入超时不能小于 1000 毫秒")
            @Max(value = 120000, message = "写入超时不能超过 120000 毫秒")
            int writeTimeoutMs
    ) {
    }

    public record MailTest(
            @NotBlank(message = "测试收件地址不能为空")
            @Email(message = "测试收件地址格式不正确")
            @Size(max = 254, message = "测试收件地址不能超过 254 个字符")
            String recipient
    ) {
    }
}
