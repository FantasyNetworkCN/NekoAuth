# NekoAuth

NekoAuth 是 FantasyNetwork 梦幻网络的账户与 OAuth 2.0 / OpenID Connect 认证服务。它以 API 为主要交付形式，只保留 OAuth 流程必需的登录、注册、授权确认、可选安装页面、最小化的 `/account` 账户中心，以及仅限 ADMIN 使用的 `/admin` 管理后台；根路径 `/` 会根据有效登录状态跳转到 `/account` 或 `/login`。默认部署复用宿主机 MySQL/MariaDB 与 Redis；Docker Compose 只启动认证服务并使用 host 网络，现有数据库与 Redis 保持不变，认证服务状态保存在 Docker 命名卷 `nekoauth_data` 中。

## 功能

- 邮箱验证码注册、用户名/邮箱登录、安全退出
- 15 分钟 JWT 访问令牌，浏览器内存存储，不写入 localStorage
- 14 天刷新令牌，`HttpOnly + SameSite` Cookie 保存
- 刷新令牌轮换、SHA-256 哈希落库、重放检测与按设备撤销令牌链；一个设备或旧标签页异常刷新不会注销其他设备
- 修改密码后立即失效所有旧访问令牌与刷新会话
- 通过邮箱验证码找回密码，成功后同样立即失效旧访问令牌与刷新会话
- 用户登录后可通过 `/account` 修改昵称、查看账户资料、修改密码、管理登录设备与退出全部会话
- 管理员通过 `/admin` 网页或 `/api/admin/**` 管理用户、角色、账户状态、OAuth 应用与运行设置
- 注册邮箱默认只允许 `qq.com`、`gmail.com`、`163.com`、`139.com`、`126.com`、`outlook.com`、`hotmail.com`、`icloud.com`，可在 `/admin/settings` 修改
- `/admin/settings` 可维护注册开关、邮箱域名白名单、SMTP 服务器、认证信息、发件地址与发件人名称；SMTP 同时承担注册验证码与测试邮件发送
- 用户密码使用 Argon2i（10 次、64 MiB、并行度 1）
- 表结构由 JPA 实体自动管理，支持 H2、PostgreSQL、MySQL、MariaDB
- 仅保留猫娘主题的登录、注册、账户中心、OAuth 授权确认、可选安装页与管理员后台；`/account` 需要登录，`/admin`、`/admin/clients`、`/admin/settings` 仅限 ADMIN，`/`、`/app/**` 返回 404
- OAuth 2.0 授权码 + PKCE S256，OIDC 发现、JWKS、UserInfo、自省与撤销
- OAuth 访问令牌按 scope 最小化声明；管理员通过 `/admin/clients` 或 `/api/admin/oauth-clients` 管理回调地址、scope、公共/机密客户端与密钥轮换
- 兼容 GitHub 风格的 `user:email` 请求，并规范化为 OIDC 的 `email` scope
- 可选的一次性安装向导，供非 Compose 的本机或特殊部署使用

## 技术栈

- Java 27 + Spring Boot 4.1
- Spring Security 7、Spring Data JPA、Hibernate
- MySQL 8 / MariaDB 10.6+、Redis 7、H2 / PostgreSQL
- JJWT 0.13、Vue 3.5、TypeScript、Vite、Vue Router

## 快速开始（Docker Compose，复用宿主机服务）

要求 Docker 24+ 与 Compose v2，并确保宿主机上的 MySQL/MariaDB 与 Redis 已运行。Compose 只创建 `auth` 容器，使用 host 网络直接连接 `127.0.0.1:3306` 与 `127.0.0.1:6379`，不会创建 MySQL、Redis 容器；认证服务状态保存在 Docker 命名卷 `nekoauth_data` 中。

持久化目录：

- `nekoauth_data:/app/data`：保存 `nekoauth-setup.yml`、`.setup-complete` 与 OAuth 签名私钥；不会挂载当前目录的 `data/` 或 `config/`。
- 启动级覆盖项通过 Compose 环境变量提供；`config/` 目录不会挂载进容器。
- 默认使用宿主机 UID/GID `1000:1000` 访问卷内文件；其他用户请在 `.env` 设置 `NEKO_UID` 与 `NEKO_GID`。

确保旧 NekoAuth 已停止且 `65534` 端口空闲，然后启动：

```bash
printf 'NEKO_UID=%s\nNEKO_GID=%s\n' "$(id -u)" "$(id -g)" > .env

# 首次部署：构建镜像并创建命名卷
sudo docker compose build
sudo docker volume create nekoauth_data

# 仅首次从旧的 ./data 迁移状态；已有卷时跳过这一步
seed=$(sudo docker create --user root -v nekoauth_data:/app/data --entrypoint sh nekoauth:local -c 'true')
sudo docker cp data/. "$seed":/app/data
sudo docker run --rm --user root -v nekoauth_data:/app/data --entrypoint chown nekoauth:local -R "$(id -u):$(id -g)" /app/data
sudo docker rm "$seed"

sudo docker compose up -d
sudo docker compose ps
sudo docker compose logs -f --tail=200 auth
```

命名卷中的 `nekoauth-setup.yml` 会提供数据库、Redis、JWT 密钥与管理员配置；Compose 环境额外启用 HTTPS Cookie、生产 issuer 与 CORS。停止服务使用 `docker compose down`，不会删除宿主机 MySQL/Redis 或 `nekoauth_data` 卷。

首次启动时 Hibernate 会按 JPA 实体自动建表或补齐缺失的列，并在用户名和邮箱都不存在时创建 `auth.bootstrap-admin` 指定的 ADMIN；之后修改配置不会覆盖已有账户。通过安装向导完成安装时会额外写入一次性标记 `data/.admin-bootstrap-pending`，下一次启动会以安装页填写的用户名、邮箱和密码同步该 ADMIN（即使数据库中已有同名残留在测试环境的账户），成功后标记自动删除，之后再重启不会覆盖后台修改过的密码。管理员只能通过直接访问 `/admin` 进入管理后台，登录页和其他公共页面均不显示管理员入口；也可直接调用 `/api/admin/**` 管理用户、OAuth 客户端与运行设置。用户登录后进入 `/account`，可修改昵称、修改密码和管理自己的登录会话，其余身份资料保持只读。

## 管理入口

管理后台不提供公共的可点击入口，也不会作为普通登录后的落点；请管理员手动在浏览器打开 `https://<认证服务域名>/admin`。未登录时会跳转到 `/login?redirect=/admin`，使用 ADMIN 账户登录后会返回管理页；普通成员直接访问管理地址时只会看到“没有管理员权限”的提示。

管理后台包含：

- `/admin`：用户名册、角色调整与账户启停
- `/admin/clients`：OAuth 应用、回调地址、scope 与客户端密钥管理
- `/admin/settings`：注册开关、邮箱域名白名单、SMTP、发件地址与测试邮件

生产环境也可以直接调用 `/api/admin/**` 完成同样操作。根路径 `/` 返回 SPA，并根据有效登录状态跳转到 `/account` 或 `/login`；已移除的 `/app/**` 路由仍返回 404。

## 账户中心

普通用户登录或注册完成后进入 `/account`。该页面只提供最小化的自助能力：

- 修改昵称（1-32 个字符，不能包含控制字符或违禁词，允许重复）；用户名、邮箱、用户 ID、加入时间与最近登录时间保持只读。
- 修改密码；成功后服务端会让所有旧访问令牌与刷新会话立即失效，并要求重新登录。
- 查看当前账户的活跃登录设备，可按会话撤销其他设备，或退出全部设备。登录与注册时会尽力采集 Client Hints、平台版本、设备型号、架构、屏幕、时区与触控特征，以便区分手机、平板和电脑；不支持 Client Hints 的浏览器会退回能力探测与 UA 提示。
- 默认不显示任何管理员入口；ADMIN 仍需手动访问 `/admin`。

### 运行设置

注册与发信设置保存在数据库的 `application_settings` 表，后台保存后立即生效，无需重启。首次读取或首次注册时会自动建立默认配置：

- 默认开放注册，邮箱域名白名单为 `qq.com`、`gmail.com`、`163.com`、`139.com`、`126.com`、`outlook.com`、`hotmail.com`、`icloud.com`。
- 域名精确匹配 `@` 后的部分，统一转小写，不支持通配符；可在设置页填写 `@qq.com` 或 `qq.com`，保存时会自动规范化。
- 获取注册邮箱验证码前必须完成一次性图形人机验证；人机验证码 5 分钟内有效，错误 3 次后失效，前端每次发送尝试后都会自动换新。
- 注册必须通过 SMTP 发送邮箱验证码；服务端强制同一邮箱 60 秒内只能发送一次、滚动 24 小时最多发送 5 次。验证码 10 分钟内有效、最多允许输错 5 次，验证成功后立即删除且不可复用。注册页会展示当前支持的邮箱域名、有效期与发送限制，这些时间参数由公开注册策略接口返回。
- SMTP 关闭或配置不完整时，注册验证码接口返回明确错误，新用户无法完成注册；启用后可使用“保存并发送测试”验证服务器、端口、加密方式、认证信息与发件地址。
- SMTP 密码不会通过 API 回显，使用由 `auth.jwt.secret` 派生的 AES-GCM 密钥加密后落库。**更换 JWT 密钥后，已保存的 SMTP 密码将无法解密，需要在设置页重新填写。**
- 端口、数据库、Redis、JWT、Cookie、CORS、管理员引导、安装向导与 OAuth 签名等启动级配置由部署配置提供；Compose 下来自命名卷内的 `/app/data/nekoauth-setup.yml` 与环境变量，不会暴露给管理员网页修改。

Compose 设置 `AUTH_SETUP_ENABLED=false`，因此 `/setup`、`/setup/**` 与 `/api/setup/**` 直接返回 HTTP 404。MySQL 与 Redis 使用宿主机现有服务，Compose 不会创建或暴露额外数据库端口。

数据库与 Redis 数据由宿主机现有服务管理；Compose 部署的 OAuth RS256 签名私钥保存在命名卷 `nekoauth_data` 的 `/app/data/oauth-signing-key.pem`，用户头像保存在 `/app/data/avatars/`。备份时必须同时备份 MySQL、头像目录与该私钥；丢失签名私钥后，已有令牌与 JWKS 将无法继续验证。

## 本地开发

要求 Java 27、Node.js 22.18+ 或 24.12+，并确保宿主机 MySQL/MariaDB 与 Redis 已运行。已有 `data/nekoauth-setup.yml` 与 `data/.setup-complete` 时，后端会直接读取当前部署状态：

```bash
# 终端 1：后端，默认监听 65534
./mvnw spring-boot:run

# 终端 2：前端，默认监听 5173，并将 /api 代理到后端
cd frontend
npm ci
npm run dev
```

访问 `http://localhost:5173/login`；涉及 OAuth 浏览器跳转时应通过后端的 `http://localhost:65534` 验证完整流程。全新环境可改用安装向导生成 `data/nekoauth-setup.yml`。

## 生产构建

生成包含前端的单一可执行 jar：

```bash
./scripts/build.sh
java -jar target/NekoAuth-1.0.0.jar
```

直接运行 jar 时，存在 `data/.setup-complete` 与 `data/nekoauth-setup.yml` 会自动加载当前数据库、Redis 与管理员配置；也可另外创建 `config/application.yml` 覆盖启动级配置。Maven 在 `generate-resources` 阶段调用 `scripts/build-frontend.sh`，Vite 将产物直接写入 `src/main/resources/site`，随后被打入 jar。只构建后端时可加 `-Dneko.frontend.skip=true`。

静态资源缓存策略：`/assets/**` 使用 Vite 文件指纹并返回 `public, max-age=31536000, immutable`；入口 HTML、SPA 路由、安装页面和全部 `/api/**` 响应返回 `Cache-Control: no-store`，避免 CDN 缓存登录页或敏感数据。

## 可选：一次性安装向导

非 Compose 场景仍可使用安装向导。不创建 `config/application.yml` 时，内置配置会使用内存 H2、保持 `auth.setup.enabled: true` 并启动安装入口，不会在项目中生成 `.db` 文件；打开 `/setup`，提交一次性令牌、管理员、MySQL/MariaDB 与 Redis 连接信息，再重启服务。配置会写入 `data/nekoauth-setup.yml`、一次性管理员标记 `data/.admin-bootstrap-pending` 与完成标记 `data/.setup-complete`，重启后启用真实数据库并令安装入口返回 404。管理员标记在安装后的首次成功启动中保证安装页密码生效，然后被删除。

未配置 `auth.setup.token` 时，一次性令牌会打印在启动日志中；安装完成后令牌立即失效。生产环境推荐使用前面的 Compose 直连模式，并保持安装向导关闭。

## OAuth 2.0 与 OIDC

NekoAuth 同时是 FantasyNetwork 的 OAuth 2.0 / OpenID Connect 授权服务。管理员通过 `/admin/clients` 或 `/api/admin/oauth-clients` 登记应用与精确回调地址；用户通过 `/authorize` 查看并确认公开的权限范围。除 OAuth 所需的登录、注册、授权确认页和管理员后台外，服务不提供业务页面。

`oauth.issuer` 会写入发现文档、ID Token 的 `iss` 与端点地址，必须是客户端实际访问的公网根地址（例如 `https://auth.fantasy.network`，末尾不要带 `/`）。反向代理需要保留 `Host` 并传递 `X-Forwarded-Proto`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/.well-known/openid-configuration` | OIDC 发现文档 |
| `GET` | `/.well-known/oauth-authorization-server` | OAuth 授权服务器元数据 |
| `GET/POST` | `/oauth2/authorize` | 浏览器授权入口，接受查询参数或表单提交并跳转到同意页 |
| `POST` | `/oauth2/token` | 授权码交换与刷新令牌轮换 |
| `GET` | `/oauth2/jwks` | RS256 公钥集合 |
| `GET/POST` | `/oauth2/userinfo` | 使用访问令牌读取用户声明 |
| `POST` | `/oauth2/introspect` | 令牌自省 |
| `POST` | `/oauth2/revoke` | 撤销刷新令牌或访问令牌 |
| `GET/POST` | `/oauth2/logout` | OIDC RP-Initiated Logout；会话匹配时直接登出，无 hint 或会话不匹配时转到确认页 |

公共客户端必须使用 PKCE `S256`，且不能持有客户端密钥；机密客户端可通过 HTTP Basic 或表单参数提交密钥。回调地址必须与登记值完全一致；非本机回调地址只允许 `https://`，明文 `http://` 仅可用于 `localhost`、`127.0.0.1` 或 `[::1]` 本机开发。原生公共客户端可以使用已登记的私有协议回调（例如 `com.example.app:/oauth/callback`），机密客户端不允许私有协议回调。

授权请求支持 OIDC 的 `prompt=none|login|consent|select_account`、`login_hint` 与 `max_age`。`prompt=none` 在无法静默完成授权时返回 `login_required` 或 `consent_required`；`max_age=0`、`prompt=login` 或 `prompt=select_account` 会要求重新认证。ID Token 的 `auth_time` 来自实际登录时间，并在刷新令牌轮换时保持不变。

登出申请通过 `id_token_hint` 与已登记的 `post_logout_redirect_uri` 校验目标地址；可选 `state` 会原样追加到回调。签名、issuer、audience/azp 合法但已过期的 `id_token_hint` 仍可用于登出。服务会将 hint 中的用户与当前浏览器刷新会话关联：只有二者匹配时才直接清理会话；没有 hint、没有当前会话或 hint 属于其他用户时，会跳转到 `/logout/confirm`，由用户明确确认后才执行登出。会话匹配时只会撤销发起应用对应的 OAuth 刷新令牌，不影响其他平台。发现文档中的 `end_session_endpoint` 指向 `/oauth2/logout`。

### 第一方服务端账号桥接

NekoMusic 等第一方应用不再保存用户密码，也不再自行签发或保存用户 Token。它们的旧登录、注册、验证码、改密与找回密码 API 保持原路径和请求格式，由应用后端通过下面的内部接口转发到 NekoAuth；浏览器不会获得客户端密钥，也不会被签发 NekoAuth 浏览器会话。NekoMusic 浏览器登录使用标准 OAuth 授权码流程，服务端在回调中用短期 OAuth access token 换取 NekoAuth 所有的长期第一方 access token，再通过一次性交接交给浏览器。该长期令牌只能由 NekoAuth 吊销或随密码变更、账户停用失效，NekoMusic 不再维护用户 token 键值。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/internal/accounts/login` | 校验邮箱密码并返回账号身份 |
| `POST` | `/api/internal/accounts/register/code` | 发送注册邮箱验证码 |
| `POST` | `/api/internal/accounts/register` | 校验验证码并创建账号，可传可选 `nickname` |
| `POST` | `/api/internal/accounts/admin-create` | 由已校验权限的第一方后台创建普通用户，可传可选 `nickname`，跳过验证码 |
| `POST` | `/api/internal/accounts/nickname` | 修改指定邮箱账号的昵称；NekoMusic 修改昵称时调用 |
| `POST` | `/api/internal/accounts/avatar` | 第一方服务端迁移或代传头像；提交 `authUserId` 与 multipart `avatar` |
| `POST` | `/api/internal/accounts/lookup` | 按稳定用户 ID 批量查询账号摘要（邮箱、用户名、当前昵称），单次最多 200 个 |
| `POST` | `/api/internal/accounts/search` | 按昵称、用户名或邮箱搜索账号摘要，最多返回 20 个 |
| `POST` | `/api/internal/accounts/password/reset-code` | 发送找回密码验证码，保持账号不可枚举 |
| `POST` | `/api/internal/accounts/password/reset` | 验证验证码并重置密码 |
| `POST` | `/api/internal/accounts/password/change` | 校验当前密码后修改密码 |
| `POST` | `/api/internal/accounts/password/admin-reset` | 由已校验权限的第一方后台重置密码 |
| `POST` | `/api/internal/accounts/token/exchange` | 把同一机密客户端签发的短期 access token 换成长期第一方 access token |

昵称实时查询接口只返回账号摘要，不返回令牌或密码：

```json
// POST /api/internal/accounts/lookup
{ "authUserIds": [12, 34] }

// POST /api/internal/accounts/search
{ "query": "猫娘" }
```

```json
[
  { "id": 12, "email": "cat@example.com", "username": "neko_12", "nickname": "猫娘" }
]
```

长期令牌兑换请求体与响应：

```json
{ "accessToken": "<同一客户端签发的短期 OAuth access token>" }
```

```json
{
  "accessToken": "<NekoAuth 长期第一方 access token>",
  "tokenType": "Bearer",
  "expiresIn": 31536000
}
```

这些接口不接受用户 JWT，只接受 HTTP Basic 客户端认证。客户端必须同时满足：已启用、`publicClient=false`，并且 scope 明确包含 `account:bridge`。`account:bridge` 不会出现在公开发现文档中，也不能用于浏览器授权请求；它只表示“允许这个可信后端调用账号桥接接口”。管理员创建、管理员重置等接口的可信边界是调用方 NekoMusic 后端——NekoMusic 必须先校验自己的管理员权限，NekoAuth 不会猜测其后台角色。

客户端 ID 和密钥都不写死在代码中。部署时在 `/admin/clients` 创建或编辑客户端，在“申请权限”中勾选“账号桥接”（`account:bridge`）；选择该 scope 后界面会强制使用机密客户端。该 scope 只通过管理员接口 `GET /api/admin/oauth-clients/scopes` 返回，不出现在公开发现文档或用户授权页。然后把同一客户端的 ID 与密钥填入 NekoMusic 的 `oauth.client_id`、`oauth.client_secret`，并将 `oauth.issuer` 指向本服务公网根地址。同一客户端若还要完成 NekoMusic 浏览器 OAuth 登录，再按需加入 `openid profile email roles` 等公开 scope；只做旧 API 桥接时，`account:bridge` 即可。示例：

```bash
curl -sS https://auth.nekocore.cn/api/admin/oauth-clients \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "NekoMusic 服务端",
    "description": "NekoMusic 账号桥接",
    "redirectUris": ["https://music.nekocore.cn/oauth/nekocore/callback"],
    "scopes": ["openid", "profile", "email", "roles", "account:bridge"],
    "publicClient": false
  }'
```

创建响应中的 `clientSecret` 只显示一次，只能保存在 NekoMusic 服务端设置中，不能下发到浏览器或客户端安装包。轮换密钥后必须同步更新 NekoMusic；只改一端会得到 `401 INVALID_CLIENT`。

若仍返回 `401 INVALID_CLIENT`，先查看 NekoAuth 日志中的 `reason`：`not_found_or_disabled` 表示 NekoMusic 保存的 client_id 与实际客户端不一致或客户端被停用；`secret_mismatch` 表示两端密钥不同；`public_client` 表示误建成了公共客户端；`missing_account_bridge_scope` 表示忘了勾选“账号桥接”。修改 scope 不会自动轮换密钥；需要时在客户端卡片上执行“轮换密钥”，再一次性同步到 NekoMusic。日志不会输出客户端密钥。

桥接创建账号仍然执行 NekoAuth 的邮箱验证码、注册开关、邮箱域名白名单、密码策略和昵称违禁词校验；注册请求可附加可选 `nickname`，未提供时沿用邮箱前缀，只有已经由 NekoMusic 管理员后台鉴权的 `admin-create` 会跳过验证码与注册开关。登录与注册成功时，桥接响应除稳定数字用户 ID、邮箱、用户名、昵称、角色和启用状态外，还返回 `accessToken`、`tokenType` 与 `expiresIn`。该 access token 由 NekoAuth 签发并受 NekoAuth 管理，默认有效期为 365 天。NekoAuth 是昵称唯一权威源；NekoMusic 只用其中的稳定用户 ID 绑定本地业务资料，不保存昵称列，展示、搜索与通知场景通过 `lookup` / `search` 实时读取当前昵称。

NekoMusic 浏览器登录时，服务端先用授权码和客户端密钥调用 `/oauth2/token`，再把拿到的短期 access token 提交给 `/api/internal/accounts/token/exchange` 换取长期第一方 access token。交换只接受同一客户端签发、未过期、未被吊销且用户会话版本仍有效的令牌；不同客户端的令牌会被拒绝。浏览器只收到 NekoMusic 的一次性登录交接，再由完成页领取 token，URL 中不会出现 access token。

NekoMusic 后续每次用户鉴权通过 `/oauth2/introspect` 获取权威身份，响应中的 `name` 为当前昵称；登出通过 `/oauth2/revoke` 吊销 access token。`/oauth2/introspect` 对已吊销、已过期、用户停用或密码变更后的旧令牌返回 `active=false`；`/oauth2/revoke` 按 RFC 7009 对无效令牌同样返回成功。密码、验证码、昵称和账号状态始终以 NekoAuth 为唯一权威源。

`oauth.enabled` 只控制浏览器 OAuth 登录是否开放，不替代桥接所需的客户端配置。桥接请求失败时，内部接口返回统一业务错误与必要的 `Retry-After`；调用方应原样映射冷却时间，不要自行重试发送验证码。

### NekoAI / New API 接入

以下配置以 NekoAI 当前部署为例：认证服务位于 `https://auth.nekocore.cn`，New API 位于 `https://ai.nekocore.cn`，New API 自定义 OAuth Provider 的 `slug` 为 `nekoai`。部署到其他域名时只需替换对应根地址。

#### 1. 在 NekoAuth 登记客户端

先通过 `/api/auth/login` 获取管理员访问令牌，再调用管理 API 新建一个**机密客户端**。New API 在服务端交换授权码并调用 UserInfo，因此必须保存响应中的 `clientSecret`，不能登记为公共客户端：

```bash
curl -sS https://auth.nekocore.cn/api/admin/oauth-clients \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "NekoAI",
    "description": "NekoAI / New API",
    "redirectUris": ["https://ai.nekocore.cn/oauth/nekoai"],
    "postLogoutRedirectUris": ["https://ai.nekocore.cn"],
    "scopes": ["openid", "profile", "email", "offline_access"],
    "publicClient": false
  }'
```

| NekoAuth 字段 | 值 |
| --- | --- |
| 名称 | `NekoAI`（可自定义） |
| 回调地址 | `https://ai.nekocore.cn/oauth/nekoai` |
| 客户端类型 | 机密客户端 |
| Scopes | `openid profile email offline_access`；需要角色时再加 `roles` |

回调地址必须与 NekoAI 实际回调**逐字符完全一致**。New API 的回调由 `ServerAddress + /oauth/ + slug` 组成，因此 `https://ai.nekocore.cn/oauth/{slug}` 只是说明模板，登记时不能保留字面量 `{slug}`。`slug` 是 New API Provider 的路由标识，不是 OAuth 的 `client_id`。

创建客户端时会一次性返回 `client_secret`，之后列表接口不会再次显示。若丢失或泄露，请在 NekoAuth 中轮换密钥，并同步更新 NekoAI；只轮换一端会导致 `invalid_client`。

#### 2. 在 NekoAI / New API 填写 Provider

| New API 字段 | 值 |
| --- | --- |
| Provider Name | `NekoAI`（可自定义） |
| Slug | `nekoai` |
| Well-Known URL | `https://auth.nekocore.cn/.well-known/openid-configuration` |
| Client ID | 在 NekoAuth 创建的 `fn_...` 客户端 ID |
| Client Secret | 创建或轮换时返回的客户端密钥 |
| Authorization Endpoint | `https://auth.nekocore.cn/oauth2/authorize` |
| Token Endpoint | `https://auth.nekocore.cn/oauth2/token` |
| User Info Endpoint | `https://auth.nekocore.cn/oauth2/userinfo` |
| Scopes | `openid profile email offline_access` |
| Auth Style | `Params (in body)` / `client_secret_post` |
| User ID Field | `sub` |
| Username Field | `preferred_username` |
| Display Name Field | `name` |
| Email Field | `email` |

New API 的字段名区分大小写，且会按 `gjson` 路径读取 UserInfo JSON。NekoAuth **不会返回 `id` 字段**；User ID 固定使用 OIDC 标准声明 `sub`。把 User ID Field 填成 `id` 会导致 New API 报“获取用户信息为空，请检查设置”，即使邮箱与其他字段已经正常返回。

New API 的 Well-Known 自动填充只会补全发现文档中的端点，通常不会自动填写 User ID Field，也可能只补全 `openid profile email` 而没有 `offline_access`。保存前必须手动确认以下字段：`User ID Field=sub`、`Username Field=preferred_username`、`Display Name Field=name`、`Email Field=email`，并确认 scopes 包含 `offline_access`。

NekoAuth 同时支持 HTTP Basic 与表单参数两种客户端认证。New API 推荐使用 `Params (in body)`；两种方式不能同时提交，否则 NekoAuth 会返回 `invalid_request`。若使用 Basic，则 New API 的 Auth Style 应选择 `Header (Basic Auth)`。

#### 3. Scope 与 UserInfo 返回字段

NekoAuth 按 scope 最小化返回声明。UserInfo 无论如何都会返回 `sub` 和 `client_id`，其余字段如下：

| Scope | UserInfo 字段 | ID Token 字段 | 说明 |
| --- | --- | --- | --- |
| `openid` | `sub`、`client_id` | `sub`、`iss`、`aud`、`azp`、`exp`、`iat`、`auth_time`、`at_hash` | 签发 ID Token；访问令牌仍会正常签发 |
| `profile` | `preferred_username`、`name`、`picture`、`updated_at` | `preferred_username`、`name`、`picture` | 用户名、昵称、头像地址与更新时间 |
| `email` | `email` | `email` | 邮箱地址；注册时的验证码校验仍按当前注册策略执行 |
| `roles` | `role` | `role` | 用户角色；访问令牌中也包含 `role` |
| `offline_access` | 不增加 UserInfo 字段 | 不增加字段 | 授权码交换时额外签发 refresh token |

`user:email` 是 GitHub 风格的兼容别名，会规范化为 `email`，不会同时启用 `profile`。因此只请求 `user:email` 时，UserInfo 只有：

```json
{
  "sub": "1000000",
  "email": "user@example.com",
  "client_id": "fn_your_client_id"
}
```

如果 NekoAI 还需要用户名和显示名称，应使用：

```text
openid profile email offline_access
```

也可以保留别名写法：

```text
user:email profile openid offline_access
```

完整 scope 下的 UserInfo 示例：

```json
{
  "sub": "1000000",
  "preferred_username": "neko",
  "name": "neko",
  "picture": "https://auth.nekocore.cn/avatars/1000000",
  "updated_at": 1793370000,
  "email": "user@example.com",
  "client_id": "fn_your_client_id"
}
```

只有授权请求中包含 `offline_access` 时，令牌端点才会返回 `refresh_token`；之后再调用令牌端点也无法给已有访问令牌“补加”刷新令牌，必须重新发起授权。`roles` 是可选项，不需要角色声明时不要添加。

#### 4. 刷新令牌与 NekoAI 会话

NekoAI 的 `/api/user/auth/refresh` 是 New API 自己的登录会话刷新接口，不是 NekoAuth 的 `/oauth2/token`。OAuth 登录失败时，New API 尚未建立自己的会话 Cookie，前端调用 `/api/user/auth/refresh` 返回 `401 Unauthorized` 是预期结果，不应拿这个请求判断 NekoAuth 是否正常。

NekoAuth 的刷新流程是：

```bash
curl -sS https://auth.nekocore.cn/oauth2/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=refresh_token' \
  --data-urlencode 'client_id=fn_your_client_id' \
  --data-urlencode 'client_secret=YOUR_CLIENT_SECRET' \
  --data-urlencode 'refresh_token=REFRESH_TOKEN'
```

机密客户端也可以使用 Basic 认证。每次刷新都会轮换 refresh token；旧令牌再次出现会触发重放检测，并撤销同一用户在该客户端下的授权链。

#### 5. 常见错误对照

| 现象 | 原因与处理 |
| --- | --- |
| “获取用户信息为空，请检查设置” | New API 按 `User ID Field` 没有取到值。最常见原因是填了 `id`，应改为 `sub`。 |
| UserInfo 有 `sub` 但没有用户名或昵称 | 只请求了 `user:email`；增加 `profile` 后重新授权。 |
| 登录成功但 New API 没有 refresh token | 授权请求缺少 `offline_access`；修改 scope 后重新发起授权。 |
| “获取用户信息失败” | UserInfo 返回非 200。查看 New API 日志中的 `GetUserInfo failed: status=...`：`401` 检查 Bearer 头与 access token，`404` 检查 User Info Endpoint。 |
| `invalid_client` | Client ID 或 Client Secret 错误、密钥只轮换了一端，或同时使用 Basic 与表单参数。 |
| `invalid_grant` | 授权码已使用/已过期、`redirect_uri` 不一致，或 refresh token 已轮换/撤销。 |
| `invalid_scope` | 请求了 NekoAuth 客户端未登记的 scope。先在该 OAuth 应用配置中增加对应 scope。 |
| 回调提示 `redirect_uri 未在应用中登记` | NekoAuth 中登记的回调必须与 New API 实际回调完全一致，包括协议、域名、路径与末尾 `/`。 |
| 浏览器报 `ERR_HTTP2_PROTOCOL_ERROR` 或白屏 | 不要同时启用源站与 CDN 压缩；保持 `server.compression.enabled: false`，由 CDN 统一压缩，并清理 CDN 的 `/assets/*` 缓存。 |

NekoAuth 的 `WWW-Authenticate` 响应头只包含 ASCII 的 `Bearer error="invalid_token"`，中文错误说明放在 JSON 的 `error_description` 中。若日志出现 `WWW-Authenticate ... removed ... UnmappableCharacterException`，说明运行的是旧版本，应重新构建并部署。

排查 UserInfo 时可以用无令牌和伪令牌各请求一次：

```bash
curl -sS -i https://auth.nekocore.cn/oauth2/userinfo
curl -sS -i https://auth.nekocore.cn/oauth2/userinfo \
  -H 'Authorization: Bearer dummy-token'
```

第一次应返回“缺少访问令牌”，第二次应返回“访问令牌无效或已过期”。如果第一次正常而 NekoAI 仍报 UserInfo 失败，请直接查看 New API 服务端的 `[OAuth-Generic-<slug>] ExchangeToken response status`、`GetUserInfo response status` 与 `GetUserInfo failed` 日志；NekoAuth 登录日志不能替代 New API 的 Provider 日志。

### 接入示例

先生成长度 43–128 的 `code_verifier`，再计算 `BASE64URL(SHA-256(code_verifier))` 作为 `code_challenge`：

```text
GET https://auth.fantasy.network/oauth2/authorize
  ?client_id=fn_your_client_id
  &redirect_uri=https%3A%2F%2Fapp.example.com%2Foauth%2Fcallback
  &response_type=code
  &scope=openid%20profile%20email
  &state=random-state
  &nonce=random-nonce
  &code_challenge=BASE64URL_SHA256_VERIFIER
  &code_challenge_method=S256
```

用户确认后回调会带上 `code` 与原始 `state`。公共客户端用以下请求交换令牌：

```bash
curl -sS https://auth.fantasy.network/oauth2/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=authorization_code' \
  --data-urlencode 'client_id=fn_your_client_id' \
  --data-urlencode 'code=AUTHORIZATION_CODE' \
  --data-urlencode 'redirect_uri=https://app.example.com/oauth/callback' \
  --data-urlencode 'code_verifier=YOUR_CODE_VERIFIER'
```

机密客户端使用 Basic 认证（也可通过 `client_id` / `client_secret` 表单参数）：

```bash
curl -sS https://auth.fantasy.network/oauth2/token \
  -u 'fn_your_client_id:YOUR_CLIENT_SECRET' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=authorization_code' \
  --data-urlencode 'code=AUTHORIZATION_CODE' \
  --data-urlencode 'redirect_uri=https://app.example.com/oauth/callback'
```

刷新令牌与 UserInfo：

```bash
curl -sS https://auth.fantasy.network/oauth2/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=refresh_token' \
  --data-urlencode 'client_id=fn_your_client_id' \
  --data-urlencode 'refresh_token=REFRESH_TOKEN'

curl -sS https://auth.fantasy.network/oauth2/userinfo \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

授权码只能使用一次；刷新令牌每次使用都会轮换，旧令牌再次出现时会撤销对应授权链。RS256 签名私钥首次启动时生成并持久化到 `oauth.key-file`（Compose 命名卷中的 `/app/data/oauth-signing-key.pem`），请与数据库一起备份并限制读取权限。

## Docker Compose 运维

```bash
# 查看服务状态与启动日志
docker compose ps
docker compose logs -f auth

# 更新代码后重建认证服务
docker compose up -d --build auth

# 仅停止认证容器，宿主机 MySQL/Redis 与 nekoauth_data 卷均保留
docker compose down
```

生产环境应把认证服务放在 HTTPS 反向代理之后，设置 `auth.cookie.secure: true`，并确保 `oauth.issuer` 与外部访问根地址完全一致。反向代理需保留 `Host` 并传递 `X-Forwarded-Proto`；`auth.cors.allowed-origins` 必须包含认证站点自身的完整 Origin（例如 `https://auth.nekocore.cn`），否则登录 POST 会被 Spring 以 `Invalid CORS request` 拒绝。已启用 OAuth 客户端登记的 `https://` / `http://` 回调地址与登出回调地址会自动把其 Origin 加入 OIDC/OAuth 端点（`/.well-known/**` 与 `/oauth2/**`）的允许列表，无需重复配置。源站默认关闭 HTTP 压缩，由 CDN 或反向代理统一启用 gzip/brotli；不要在源站与 CDN 同时压缩，否则部分 CDN 会保留未压缩的 `Content-Length`，导致浏览器报 `ERR_HTTP2_PROTOCOL_ERROR` 并白屏。

认证状态保存在命名卷 `nekoauth_data`；`docker compose down` 会保留该卷，但 `docker compose down -v` 会删除它。不要删除卷内的 `oauth-signing-key.pem`，否则已有令牌与 JWKS 将无法继续验证。

## 配置

生产 Compose 使用 host 网络，认证状态保存在命名卷 `nekoauth_data`，数据库、Redis、JWT 密钥与管理员引导来自卷内的 `/app/data/nekoauth-setup.yml`。需要覆盖 Cookie、issuer、CORS 或数据库连接时，使用 Compose 环境变量；`config/` 目录不会挂载进容器，Compose 也不会生成隔离数据库或密钥。

时间统一使用中国标准时间 UTC+8（`Asia/Shanghai`），不按 UTC 存储或输出。JDBC 连接参数、Hibernate JDBC 时区与 Jackson 时区必须保持一致；MySQL 使用 `serverTimezone=Asia/Shanghai`，MariaDB 使用 `timezone=Asia/Shanghai`。数据库中的 `datetime/timestamp` 墙钟值直接表示 UTC+8；API 中的 `Instant` 统一序列化为带 `+08:00` 偏移的 ISO-8601 字符串（例如 `2026-10-31T02:00:00+08:00`），不会输出 `Z`，客户端不需要再做时区换算。

| YAML 配置项 | 内置默认值 | 说明 |
| --- | --- | --- |
| `server.port` | `65534` | HTTP 端口 |
| `server.compression.enabled` | `false` | 源站 HTTP 压缩；CDN 已启用压缩时保持关闭 |
| `spring.datasource.url` | H2 内存数据库 | Docker 复用命名卷内 `nekoauth-setup.yml` 的宿主机 MySQL/MariaDB 地址；内存库仅用于安装向导和临时调试 |
| `spring.datasource.username` | `sa` | Docker 由命名卷内 `nekoauth-setup.yml` 提供 |
| `spring.datasource.password` | 空 | Docker 由命名卷内 `nekoauth-setup.yml` 提供 |
| `spring.data.redis.host` | 未设置 | 设置后启用 Redis 共享登录限流 |
| `spring.data.redis.port` | `6379` | Redis 端口 |
| `spring.data.redis.database` | `0` | Redis 数据库编号 |
| `spring.data.redis.password` | 空 | Redis 密码；未启用认证时留空 |
| `auth.setup.enabled` | `true` | 关闭后安装入口直接返回 404；生产设为 `false` |
| `auth.setup.state-dir` | `./data` | 安装配置与完成标记目录；Compose 中为命名卷内的 `/app/data` |
| `auth.setup.token` | 自动生成 | 可选安装向导的一次性令牌 |
| `auth.jwt.secret` | 开发默认值 | JWT HMAC 密钥；生产必须使用至少 32 字节随机值 |
| `auth.jwt.issuer` | `neko-auth` | JWT issuer |
| `auth.jwt.access-ttl` | `15m` | 访问令牌有效期 |
| `auth.jwt.refresh-ttl` | `14d` | 刷新令牌有效期 |
| `auth.cookie.secure` | `false` | HTTPS 部署时设为 `true` |
| `auth.cookie.same-site` | `Strict` | 刷新 Cookie 的 SameSite |
| `auth.cors.allowed-origins` | `http://localhost:5173`, `https://auth.nekocore.cn` | 认证站点的浏览器 Origin；OIDC/OAuth 端点还会自动允许已启用客户端回调地址的 Origin |
| `auth.rate-limit.attempts` | `20` | 单 IP 限流窗口内失败次数 |
| `auth.rate-limit.window` | `5m` | 登录限流窗口 |
| `auth.registration.require-email-verification` | `true` | 注册是否必须通过邮箱验证码；生产保持开启 |
| `auth.registration.email-code-ttl` | `10m` | 注册邮箱验证码有效期；同时通过公开注册策略接口返回 |
| `auth.registration.email-code-cooldown` | `60s` | 同一邮箱两次发送注册验证码的最小间隔，由服务端强制 |
| `auth.registration.email-code-daily-limit` | `5` | 同一邮箱在发送窗口内最多可请求的验证码数量 |
| `auth.registration.email-code-window` | `24h` | 注册验证码发送计数窗口；从该窗口首次发送开始滚动计算 |
| `auth.registration.captcha-ttl` | `5m` | 注册图形人机验证码有效期 |
| `auth.bootstrap-admin.username` | 空 | 首次启动创建管理员的用户名；需与邮箱、密码同时设置 |
| `auth.bootstrap-admin.email` | 空 | 首次启动创建管理员的邮箱 |
| `auth.bootstrap-admin.password` | 空 | 初始管理员密码；长度 6-30 个字符 |
| `oauth.issuer` | `http://localhost:65534` | OAuth/OIDC issuer 与元数据根地址；公网部署必须是 HTTPS |
| `oauth.key-file` | `./data/oauth-signing-key.pem` | RS256 签名私钥路径；Compose 下保存在命名卷的 `/app/data` |
| `oauth.authorization-code-ttl` | `5m` | 授权码有效期 |
| `oauth.access-token-ttl` | `1h` | OAuth 访问令牌有效期 |
| `oauth.bridge-token-ttl` | `365d` | 第一方账号桥接登录、注册与 token 交换签发的长期 access token 有效期 |
| `oauth.id-token-ttl` | `10m` | OIDC ID Token 有效期 |
| `oauth.refresh-token-ttl` | `30d` | OAuth 刷新令牌有效期 |

管理员初始化在未检测到一次性安装标记时，只在该用户名和邮箱都不存在时创建账户，不会在后续重启时重置密码或角色。若安装标记存在且用户名或邮箱已属于已有账户，首次启动会同步安装页填写的用户名、邮箱、密码、ADMIN 角色与启用状态；若用户名和邮箱分别属于两个不同账户，启动会明确失败，要求先清理冲突数据。

## API

访问令牌通过 `Authorization: Bearer <token>` 传递，刷新令牌只通过 Cookie 传递。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/setup/status` | 可选安装状态；安装完成或禁用后返回 404 |
| `POST` | `/api/setup/install` | 校验并保存安装配置；需要 `X-Setup-Token` |
| `GET` | `/api/auth/register/captcha` | 获取一次性注册图形人机验证码、挑战 ID 与有效期 |
| `POST` | `/api/auth/register/code` | 完成人机验证后向白名单邮箱发送 6 位注册验证码；提交 `email/captchaId/captchaAnswer`，需要先配置 SMTP |
| `POST` | `/api/auth/register` | 使用可选的 `nickname` 与 `email/password/verificationCode` 注册并登录；昵称留空时使用邮箱前缀 |
| `GET` | `/api/auth/registration-policy` | 公开查询注册是否开放、是否需要邮箱验证码、邮箱域名白名单，以及验证码有效期、发送冷却、每日上限与计数窗口 |
| `POST` | `/api/auth/login` | 用户名或邮箱登录 |
| `POST` | `/api/auth/refresh` | 轮换刷新令牌并签发新访问令牌 |
| `POST` | `/api/auth/logout` | 退出当前会话 |
| `GET` | `/api/auth/me` | 当前用户 |
| `PATCH` | `/api/auth/me` | 修改当前用户昵称；提交 `nickname` 并返回更新后的用户资料 |
| `POST` | `/api/auth/avatar` | 登录后上传/替换头像；multipart 字段名为 `avatar`，支持 PNG/JPEG/GIF/WebP/BMP，最大 10MiB |
| `GET` | `/avatars/default` | 公开默认头像，内容为 Auth 站点当前 favicon |
| `GET` | `/avatars/{id}` | 公开读取指定 Auth 用户头像；无头像或用户不存在时返回默认头像 |
| `POST` | `/api/auth/password` | 登录后修改密码，撤销 Auth 登录会话与已签发的 OAuth refresh token |
| `POST` | `/api/auth/password/reset-code` | 获取人机验证码后向邮箱发送找回密码验证码；提交 `email/captchaId/captchaAnswer` |
| `POST` | `/api/auth/password/reset` | 使用 `email/code/newPassword` 重置密码，并撤销全部 Auth 登录会话与 OAuth refresh token |
| `GET` | `/forgot-password` | 找回密码页面，支持图形人机验证码、邮箱验证码与新密码设置 |
| `GET` | `/api/auth/sessions` | 活跃设备会话 |
| `DELETE` | `/api/auth/sessions/{id}` | 撤销指定会话 |
| `DELETE` | `/api/auth/sessions` | 撤销全部会话 |
| `GET` | `/api/admin/users` | 管理员查询用户，支持 `query/page/size` |
| `PATCH` | `/api/admin/users/{id}` | 管理员调整角色或启停账户 |
| `GET` | `/api/admin/oauth-clients` | 管理员列出 OAuth 应用 |
| `POST` | `/api/admin/oauth-clients` | 登记 OAuth 应用并一次性返回客户端密钥 |
| `PATCH` | `/api/admin/oauth-clients/{id}` | 修改回调、scope、启用状态或客户端类型 |
| `POST` | `/api/admin/oauth-clients/{id}/secret` | 轮换机密客户端密钥 |
| `GET` | `/api/admin/settings` | 管理员读取注册策略与 SMTP 设置，密码仅返回是否已配置 |
| `PUT` | `/api/admin/settings` | 管理员更新注册开关、邮箱域名白名单与 SMTP/发件人配置 |
| `POST` | `/api/admin/settings/mail/test` | 使用已保存的 SMTP 配置发送测试邮件 |
| `POST` | `/api/internal/accounts/**` | 第一方服务端账号与 token 桥接；仅接受带 `account:bridge` 的机密客户端 Basic 认证，不适用于浏览器 |

错误响应统一为：

```json
{
  "timestamp": "2026-10-26T12:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "提交的数据不符合要求",
  "fields": { "password": "密码长度需为 6-30 个字符" }
}
```

注册请求示例：

```json
{
  "nickname": "小梦",
  "email": "neko@qq.com",
  "password": "secret123"
}
```

- `email` 长度不超过 100、统一转小写且必须唯一；域名必须命中当前注册白名单，否则返回 `400 EMAIL_DOMAIN_NOT_ALLOWED`；关闭注册时返回 `403 REGISTRATION_DISABLED`。密码长度 6-30，不强制字母与数字组合。
- `nickname` 可选，最多 32 个字符，不能包含控制字符或违禁词，允许重复；留空时取邮箱 `@` 前的部分（最多 20 个 Unicode 字符），该默认昵称同样会执行违禁词校验。创建后可在 `/account` 或 `PATCH /api/auth/me` 修改。
- 新注册用户的内部 `username` 由服务端生成并保持唯一，API 响应同时返回 `username`、`nickname` 与 `displayName`。
- 管理员设置昵称：安装向导不设置昵称时由用户名生成初始昵称，`bootstrap-admin` 仍不接受 `display-name`；管理员与普通用户一样通过 `nickname` 修改昵称。管理员的唯一登录名仍是 `username`，`nickname`、`displayName` 与 OIDC `name` 均取自可修改的 `display_name`。

昵称违禁词检测由 `SensitiveWordUtil` 统一执行，词库位于 `src/main/resources/违禁词/`：

- `主词表.txt`：中文主词表。
- `英文词表.txt`：英文补充词表；当前与 NekoMusic 一致，为空文件。
- `白名单.txt`：命中主词表但允许作为普通文本使用的词。

检测前会移除空白、零宽字符和 BOM；命中时注册、管理员建号、普通改名及桥接改名统一返回 `400 INVALID_NICKNAME`。修改词库后需要重新构建并部署 NekoAuth。

### 找回密码

找回密码使用与注册相同的图形人机验证码和邮箱验证码基础设施：

1. `GET /api/auth/register/captcha` 获取一次性图形挑战。
2. `POST /api/auth/password/reset-code` 提交 `email`、`captchaId`、`captchaAnswer`。接口不会透露邮箱是否注册：已注册邮箱会收到 6 位验证码，未注册邮箱不会发信，但响应状态与文案完全一致。
3. `POST /api/auth/password/reset` 提交 `email`、`code`、`newPassword`。验证码只能用于找回密码，不能拿去注册；验证成功后立即消费。
4. 重置成功后，用户 `tokenVersion` 递增，Auth 的 refresh token 与所有 OAuth refresh token 被撤销；现有 access token 也会因版本不匹配立即失效，客户端需要重新登录。

验证码 10 分钟有效、最多尝试 5 次；同一邮箱 60 秒内只能发送一次，滚动 24 小时最多发送 5 次。发送限流记录与验证码记录分离，验证码被消费或删除不会解除发送冷却。

注册与找回密码验证码在数据库中都只保存 HMAC-SHA256 摘要，并带有用途标记。修改密码与找回密码都会撤销 Auth 登录会话及 OAuth 授权刷新令牌；已经由 OAuth 换取到第三方应用本地的会话属于该应用自己的会话，只有应用实现对应的注销或撤销回调时才能同步失效。

### 用户 ID 与密码格式

- 用户 ID 由实体上的 `@TableGenerator` 分配，从 `1000000` 开始逐个递增；不依赖数据库厂商的 `AUTO_INCREMENT` 起始值。
- 用户密码使用 `Argon2Factory.create()` 生成的 Argon2i，参数为 `t=10`、`m=65536 KiB`、`p=1`；数据库列长度为 255。
- 旧 BCrypt 用户仍可在登录后自动升级为 Argon2i；OAuth 客户端密钥独立使用 BCrypt(12)，不受用户密码算法影响。

## 安全设计

- Compose 默认关闭安装入口；可选安装向导在完成后同样由 Servlet 过滤器直接返回 404，不依赖前端隐藏。
- 安装和服务连接过程不会把 MySQL/MariaDB、Redis 或管理员密码写入日志。
- 安装文件以临时文件 + 原子移动写入，使用 POSIX `0600` 权限；完成标记使用独占创建。
- 首次启动可由 `data/nekoauth-setup.yml`（或 `config/application.yml`）创建 ADMIN，密码只保存 Argon2i 哈希，后续重启不会覆盖已有账户；安装向导通过一次性标记让安装页密码在首次重启中生效，并在同步成功后删除标记。
- 用户密码使用 Argon2i 参数 `t=10`、`m=65536 KiB`、`p=1`；密码长度 6-30 个字符。
- 注册人机验证使用服务端生成的图形挑战，答案只保存带服务端密钥的 HMAC-SHA256 摘要；挑战一次性消费，5 分钟过期，错误 3 次后删除。
- 注册与找回密码验证码使用 `SecureRandom` 生成，数据库只保存带服务端密钥的 HMAC-SHA256 摘要，并记录用途防止跨流程复用；验证码 10 分钟过期、最多 5 次尝试，成功或失败次数耗尽后立即删除。发送限流使用独立记录，60 秒冷却与滚动 24 小时 5 次上限不随验证码删除而重置。
- 新用户 ID 从 `1000000` 开始；OAuth 客户端密钥使用 BCrypt cost 12。
- JWT 仅存 JS 内存。刷新令牌不暴露给 JavaScript，数据库只保存其 SHA-256 哈希。
- 登录设备画像是客户端“尽力提供”的信息，用于展示和风险判断，不是不可伪造的身份凭证；需要强设备认证时应叠加 WebAuthn/Passkey 或原生应用设备密钥。
- 每次刷新都会撤销旧刷新令牌；已轮换令牌再次出现时，只撤销该设备对应的刷新令牌链，不影响同一用户的其他设备或 OAuth 客户端。
- Web 端刷新通过 Web Locks 串行化同一浏览器的多个标签页，避免并发轮换被误判为令牌重放。
- 用户 `tokenVersion` 使密码修改、密码重置、角色变化或停用立即让旧 JWT 失效；修改或重置密码还会撤销全部 Auth refresh token 与 OAuth refresh token。
- API 使用无状态安全上下文；刷新 Cookie 使用全站路径、`SameSite=Strict` 和受限 CORS，以便 OIDC 登出核对当前浏览器会话。登录、注册、刷新及登出响应会清理旧版本 `/api/auth` 路径下的同名 Cookie。
- CSP 禁止内联脚本，并禁用 iframe 嵌入；HSTS 在 HTTPS 下由 Spring Security 输出。
- 公共客户端强制 PKCE S256；授权码短期有效、只保存哈希且只能交换一次。
- 回调地址精确匹配，非回环地址禁止明文 HTTP；OAuth 协议错误与业务 API 错误分别保持标准响应格式。
- OAuth 访问令牌仅在 `profile` scope 下包含用户名、仅在 `roles` scope 下包含角色；RS256 私钥落盘到受限数据目录，JWKS 仅暴露公钥。
- 管理后台保存的 SMTP 密码使用 AES-GCM 加密，设置接口不回显明文；认证失败、连接失败或测试收件地址被拒绝时，测试邮件接口返回 `502 MAIL_SEND_FAILED`，不会静默成功。

生产环境至少必须：使用 HTTPS、设置 `auth.cookie.secure: true`、保持 `auth.setup.enabled: false`、使用独立数据库账号、备份 MySQL 与命名卷 `nekoauth_data` 中的 `/app/data/oauth-signing-key.pem`，并在反向代理层增加更严格的限流与审计。

## 日志

日志默认输出到标准输出，每行包含本地时间、级别、线程、应用名、请求 ID、简写 logger 和消息；终端支持 ANSI 时会自动着色。格式示例：

```text
2026-10-31 00:40:10.235 INFO  http-nio-65534-exec-1 [neko-auth:8fd30cb0-...] n.f.a.web.RequestLoggingFilter - GET /api/auth/me -> 200 (12 ms)
```

- 每个请求都会响应 `X-Request-Id`；调用方传入 8-64 位安全字符时会沿用，否则自动生成 UUID，CORS 已暴露该响应头。
- API、OAuth、OIDC 和页面请求记录方法、路径、状态码与耗时；`/assets/**`、`/favicon.ico` 等静态资源不写访问日志。
- 访问日志只记录路径，不记录查询字符串、请求体、令牌、Cookie 或密码。
- 可通过 `NEKO_LOG_LEVEL` 调整业务日志级别，例如 `NEKO_LOG_LEVEL=DEBUG`；HikariCP 和 Hibernate 默认降到 `WARN`，减少启动噪声。
- 服务不主动写日志文件，便于 Docker、systemd 或日志平台直接收集标准输出。

## 测试

```bash
./mvnw test
cd frontend && npm run build
```

后端测试覆盖认证主流程、JWT 鉴权、刷新轮换、OAuth 刷新令牌重放与授权链撤销、scope 最小化、`user:email` 别名、UserInfo 声明、ASCII Bearer 错误头、登出、密码变更、会话撤销、管理员权限隔离、OAuth 公共/机密客户端、PKCE、回调校验、停用客户端、安装入口 404 生命周期与安装配置覆盖顺序。认证测试还覆盖 `1000000` 起始 ID、管理员昵称修改、自定义及邮箱派生昵称、昵称修改与校验、Argon2i 固定哈希、旧 BCrypt 自动升级、重复显示名、根路径 404 与管理员页面转发；设置测试覆盖默认域名白名单、非白名单拒绝、注册开关、管理员更新与 SMTP 密码不回显。

## 目录

```text
src/main/java/neko/fantasy/auth/          后端应用、领域模型、服务、安全与 API
src/main/java/neko/fantasy/auth/setup/    一次性安装流程、连接校验、配置落盘与 404 过滤器
src/main/java/neko/fantasy/auth/settings/ 运行设置持久化、注册白名单、SMTP 配置与测试发信
src/main/resources/META-INF/spring.factories  安装配置高优先级加载器
src/test/                                 集成测试与安装测试
frontend/src/                             Vue 页面、组件、API 客户端与状态
frontend/src/assets/brand/                品牌标识资源
frontend/src/assets/local/                页面主视觉与插画资源
scripts/build-frontend.sh                 Vite 前端构建到 src/main/resources/site
scripts/build.sh                          前端构建 + 后端 jar 打包
Dockerfile / compose.yml                  生产镜像与 MySQL、Redis 编排
Docker Compose 默认复用宿主机 MySQL/Redis，认证状态保存在 `nekoauth_data` 命名卷，详见 README 快速开始。
```
