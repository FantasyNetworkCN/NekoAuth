package neko.fantasy.auth.setup;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class SetupRequests {

    private SetupRequests() {
    }

    public record Install(
            @NotBlank(message = "管理员用户名不能为空")
            @Pattern(regexp = "^[A-Za-z0-9_]{3,32}$", message = "管理员用户名需为 3-32 位字母、数字或下划线")
            String adminUsername,

            @NotBlank(message = "管理员邮箱不能为空")
            @Email(message = "管理员邮箱格式不正确")
            @Size(max = 254, message = "管理员邮箱过长")
            String adminEmail,

            @Size(max = 64, message = "管理员昵称不能超过 64 个字符")
            String adminDisplayName,

            @NotBlank(message = "管理员密码不能为空")
            @Size(min = 8, max = 64, message = "管理员密码长度需为 8-64 个字符")
            String adminPassword,

            @NotBlank(message = "MySQL/MariaDB 主机不能为空")
            @Size(max = 255, message = "MySQL/MariaDB 主机过长")
            String mysqlHost,

            @NotNull(message = "MySQL/MariaDB 端口不能为空")
            @Min(value = 1, message = "MySQL/MariaDB 端口无效")
            @Max(value = 65535, message = "MySQL/MariaDB 端口无效")
            Integer mysqlPort,

            @NotBlank(message = "MySQL/MariaDB 数据库名不能为空")
            @Pattern(regexp = "^[A-Za-z0-9_]{1,64}$", message = "MySQL/MariaDB 数据库名只能包含字母、数字或下划线")
            String mysqlDatabase,

            @NotBlank(message = "MySQL/MariaDB 用户名不能为空")
            @Size(max = 128, message = "MySQL/MariaDB 用户名过长")
            String mysqlUsername,

            @Size(max = 256, message = "MySQL/MariaDB 密码过长")
            String mysqlPassword,

            @NotBlank(message = "Redis 主机不能为空")
            @Size(max = 255, message = "Redis 主机过长")
            String redisHost,

            @NotNull(message = "Redis 端口不能为空")
            @Min(value = 1, message = "Redis 端口无效")
            @Max(value = 65535, message = "Redis 端口无效")
            Integer redisPort,

            @Size(max = 256, message = "Redis 密码过长")
            String redisPassword,

            @NotNull(message = "Redis 数据库编号不能为空")
            @Min(value = 0, message = "Redis 数据库编号无效")
            @Max(value = 255, message = "Redis 数据库编号无效")
            Integer redisDatabase
    ) {
    }
}
