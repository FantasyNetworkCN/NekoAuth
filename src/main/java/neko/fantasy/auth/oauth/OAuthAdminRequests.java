package neko.fantasy.auth.oauth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class OAuthAdminRequests {

    private OAuthAdminRequests() {
    }

    public record CreateClient(
            @NotBlank(message = "应用名称不能为空")
            @Size(max = 80, message = "应用名称不能超过 80 个字符")
            String name,

            @Size(max = 500, message = "应用简介不能超过 500 个字符")
            String description,

            @NotEmpty(message = "至少需要一个回调地址")
            List<@NotBlank(message = "回调地址不能为空") @Size(max = 2000) String> redirectUris,

            @NotEmpty(message = "至少需要一个权限范围")
            List<String> scopes,

            boolean publicClient
    ) {
    }

    public record UpdateClient(
            @Size(max = 80, message = "应用名称不能超过 80 个字符")
            String name,

            @Size(max = 500, message = "应用简介不能超过 500 个字符")
            String description,

            List<@NotBlank(message = "回调地址不能为空") @Size(max = 2000) String> redirectUris,

            List<String> scopes,

            Boolean publicClient,

            Boolean enabled
    ) {
    }
}
