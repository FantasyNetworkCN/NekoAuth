# NekoAuth

NekoAuth 是 FantasyNetwork 梦幻网络的账户与 OAuth 2.0 / OpenID Connect 认证服务。它以 API 为主要交付形式，只保留 OAuth 流程必需的登录、注册、授权确认与可选安装页面；根路径 `/`、管理员后台和用户后台均不再提供。默认部署使用 MySQL 8 与 Redis 7，管理员由 `config/application.yml` 在首次启动时创建；生产 Compose 会关闭 `/setup` 安装入口。

## 功能

- 注册、用户名/邮箱登录、安全退出
- 15 分钟 JWT 访问令牌，浏览器内存存储，不写入 localStorage
- 14 天刷新令牌，`HttpOnly + SameSite` Cookie 保存
- 刷新令牌轮换、SHA-256 哈希落库、重放检测与授权链撤销
- 修改密码后立即失效所有旧访问令牌与刷新会话
- API 支持设备会话管理、密码修改与全部会话退出；不提供用户后台或个人资料页
- 管理员用户列表、角色调整、账户启停
- 用户密码使用与 NekoMusic 一致的 Argon2i（10 次、64 MiB、并行度 1），旧 BCrypt 哈希登录后自动升级
- Flyway 数据库迁移，支持 H2、PostgreSQL、MySQL、MariaDB
- 仅保留猫娘主题的登录、注册与 OAuth 授权确认页；直接访问 `/`、`/admin`、`/admin/clients` 均返回 404
- OAuth 2.0 授权码 + PKCE S256，OIDC 发现、JWKS、UserInfo、自省与撤销
- OAuth 访问令牌按 scope 最小化声明；管理员通过 `/api/admin/oauth-clients` 管理回调地址、scope、公共/机密客户端与密钥轮换
- 兼容 GitHub 风格的 `user:email` 请求，并规范化为 OIDC 的 `email` scope
- 可选的一次性安装向导，供非 Compose 的本机或特殊部署使用

## 技术栈

- Java 21 + Spring Boot 4.1
- Spring Security 7、Spring Data JPA、Flyway
- MySQL 8 / MariaDB 10.6+、Redis 7、H2 / PostgreSQL
- JJWT 0.13、Vue 3.5、TypeScript、Vite、Vue Router

## 快速开始（Docker Compose）

要求 Docker 24+ 与 Compose v2。配置使用 Spring YAML 与独立文件密钥：

```bash
cp config/application.example.yml config/application.yml
mkdir -p config/secrets

printf '%s' "$(openssl rand -base64 48)" > config/secrets/db_password
printf '%s' "$(openssl rand -base64 48)" > config/secrets/jwt_secret
printf '%s' "$(openssl rand -base64 48)" > config/secrets/mysql_root_password
printf '%s' 'ChangeMe-2026-NekoAuth' > config/secrets/admin_password
chmod 600 config/secrets/*
```

然后编辑 `config/application.yml`，至少确认以下配置：

| 配置项 | 要求 |
| --- | --- |
| `oauth.issuer` | 客户端实际访问的根地址，不带末尾 `/` |
| `auth.cookie.secure` | HTTPS 部署必须设为 `true` |
| `auth.bootstrap-admin.username` | 初始管理员用户名 |
| `auth.bootstrap-admin.email` | 初始管理员邮箱 |
| `auth.bootstrap-admin.display-name` | 初始管理员昵称 |
| `auth.setup.enabled` | 生产保持 `false` |

`config/secrets/db_password` 会同时供 Spring 数据源和 MySQL 初始化使用；`config/secrets/admin_password` 长度为 6-30 个字符，与 NekoMusic 的注册密码规则一致。Compose 会把四份文件挂载为 `/run/secrets` 下的 Docker secrets，应用容器以非 root 用户运行也能读取，不依赖宿主机文件的 UID 与 `600` 权限。`config/application.yml` 与 `config/secrets/` 默认已被 Git 和 Docker 构建上下文忽略。

启动整套服务：

```bash
docker compose up -d --build
docker compose logs -f auth
```

首次启动会执行 Flyway 迁移，并在用户名和邮箱都不存在时创建 `auth.bootstrap-admin` 指定的 ADMIN；之后修改配置不会覆盖已有账户。管理员登录后可调用 `/api/admin/**` 管理用户和 OAuth 客户端；`/login` 仅供浏览器 OAuth 流程使用，不再跳转到管理后台。

`config/application.yml` 设置 `auth.setup.enabled: false`，因此 `/setup`、`/setup/**` 与 `/api/setup/**` 直接返回 HTTP 404。MySQL 与 Redis 只在 Compose 内部网络开放，不映射到宿主机公网。

`mysql-data`、`redis-data` 与 `nekoauth-data` 分别保存数据库、Redis AOF 和 OAuth RS256 签名私钥。备份时必须同时备份 MySQL 与 `nekoauth-data`；丢失签名私钥后，已有令牌与 JWKS 将无法继续验证。

## 本地开发

要求 Java 21+、Node.js 22.18+ 或 24.12+。先用开发覆盖文件把 MySQL 与 Redis 映射到本机回环地址：

```bash
cp config/application.example.yml config/application.yml
mkdir -p config/secrets
printf '%s' "$(openssl rand -base64 48)" > config/secrets/db_password
printf '%s' "$(openssl rand -base64 48)" > config/secrets/jwt_secret
printf '%s' "$(openssl rand -base64 48)" > config/secrets/mysql_root_password
printf '%s' 'ChangeMe-2026-NekoAuth' > config/secrets/admin_password
chmod 600 config/secrets/*

docker compose -f compose.yml -f compose.dev.yml up -d mysql redis

# 终端 1：后端，默认监听 65534
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 终端 2：前端，默认监听 5173，并将 /api 代理到后端
cd frontend
npm ci
npm run dev
```

`config/application-local.yml` 会把 MySQL 与 Redis 地址切换到本机回环端口，并把 `configtree` 切回工作目录下的 `config/secrets/`，因此本机运行不需要 root 权限。访问 `http://localhost:5173/login`；Vite 开发服务器只代理 `/api`，涉及 OAuth 浏览器跳转时应通过后端的 `http://localhost:65534` 验证完整流程。

## 生产构建

生成包含前端的单一可执行 jar：

```bash
./scripts/build.sh
java -jar target/NekoAuth-1.0.0.jar
```

直接运行 jar 时，应用会从工作目录加载 `config/application.yml`；请保持 `auth.setup.enabled: false`，并提供可用的 MySQL 与 Redis。若要读取工作目录下的 `config/secrets/`，可追加 `--spring.config.import=optional:configtree:./config/secrets/`，或启用 `local` profile。Maven 在 `generate-resources` 阶段调用 `scripts/build-frontend.sh`，Vite 将产物直接写入 `src/main/resources/site`，随后被打入 jar。只构建后端时可加 `-Dneko.frontend.skip=true`。

静态资源缓存策略：`/assets/**` 使用 Vite 文件指纹并返回 `public, max-age=31536000, immutable`；入口 HTML、SPA 路由、安装页面和全部 `/api/**` 响应返回 `Cache-Control: no-store`，避免 CDN 缓存登录页或敏感数据。

## 可选：一次性安装向导

非 Compose 场景仍可使用安装向导。不创建 `config/application.yml` 时，内置配置会使用本地 H2、保持 `auth.setup.enabled: true` 并启动安装入口；打开 `/setup`，提交一次性令牌、管理员、MySQL/MariaDB 与 Redis 连接信息，再重启服务。配置会写入 `data/nekoauth-setup.yml` 与 `data/.setup-complete`，重启后启用真实数据库并令安装入口返回 404。

未配置 `auth.setup.token` 时，一次性令牌会打印在启动日志中；安装完成后令牌立即失效。生产环境推荐使用前面的 Compose 直连模式，并保持安装向导关闭。

## OAuth 2.0 与 OIDC

NekoAuth 同时是 FantasyNetwork 的 OAuth 2.0 / OpenID Connect 授权服务。管理员通过 `/api/admin/oauth-clients` 登记应用与精确回调地址；用户通过 `/authorize` 查看并确认公开的权限范围。除 OAuth 所需的登录、注册和授权确认页外，服务不提供业务页面。

`oauth.issuer` 会写入发现文档、ID Token 的 `iss` 与端点地址，必须是客户端实际访问的公网根地址（例如 `https://auth.fantasy.network`，末尾不要带 `/`）。反向代理需要保留 `Host` 并传递 `X-Forwarded-Proto`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/.well-known/openid-configuration` | OIDC 发现文档 |
| `GET` | `/.well-known/oauth-authorization-server` | OAuth 授权服务器元数据 |
| `GET` | `/oauth2/authorize` | 浏览器授权入口，跳转到同意页 |
| `POST` | `/oauth2/token` | 授权码交换与刷新令牌轮换 |
| `GET` | `/oauth2/jwks` | RS256 公钥集合 |
| `GET/POST` | `/oauth2/userinfo` | 使用访问令牌读取用户声明 |
| `POST` | `/oauth2/introspect` | 令牌自省 |
| `POST` | `/oauth2/revoke` | 撤销刷新令牌 |
| `GET/POST` | `/oauth2/logout` | OIDC RP-Initiated Logout，清理浏览器会话并撤销对应应用的 OAuth 刷新令牌 |

公共客户端必须使用 PKCE `S256`，且不能持有客户端密钥；机密客户端可通过 HTTP Basic 或表单参数提交密钥。回调地址必须与登记值完全一致；非本机回调地址只允许 `https://`，明文 `http://` 仅可用于 `localhost`、`127.0.0.1` 或 `[::1]` 本机开发。原生公共客户端可以使用已登记的私有协议回调（例如 `com.example.app:/oauth/callback`），机密客户端不允许私有协议回调。

授权请求支持 OIDC 的 `prompt=none|login|consent|select_account`、`login_hint` 与 `max_age`。`prompt=none` 在无法静默完成授权时返回 `login_required` 或 `consent_required`；`max_age=0`、`prompt=login` 或 `prompt=select_account` 会要求重新认证。ID Token 的 `auth_time` 来自实际登录时间，并在刷新令牌轮换时保持不变。

登出申请通过 `id_token_hint` 与已登记的 `post_logout_redirect_uri` 校验目标地址；可选 `state` 会原样追加到回调。签名、issuer、audience/azp 合法但已过期的 `id_token_hint` 仍可用于登出。登出只会撤销发起应用对应的 OAuth 刷新令牌，不影响其他平台。发现文档中的 `end_session_endpoint` 指向 `/oauth2/logout`。

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
| `profile` | `preferred_username`、`name`、`updated_at` | `preferred_username`、`name` | 用户名、昵称与更新时间 |
| `email` | `email`、`email_verified` | `email`、`email_verified` | 邮箱与验证状态 |
| `roles` | `role` | `role` | 用户角色；访问令牌中也包含 `role` |
| `offline_access` | 不增加 UserInfo 字段 | 不增加字段 | 授权码交换时额外签发 refresh token |

`user:email` 是 GitHub 风格的兼容别名，会规范化为 `email`，不会同时启用 `profile`。因此只请求 `user:email` 时，UserInfo 只有：

```json
{
  "sub": "1000000",
  "email": "user@example.com",
  "email_verified": true,
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
  "name": "猫屋管理员小梦",
  "updated_at": 1793370000,
  "email": "user@example.com",
  "email_verified": true,
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

授权码只能使用一次；刷新令牌每次使用都会轮换，旧令牌再次出现时会撤销对应授权链。RS256 签名私钥首次启动时生成并持久化到 `oauth.key-file`（Compose 默认数据卷中的 `data/oauth-signing-key.pem`），请与数据库一起备份并限制读取权限。

## Docker Compose 运维

```bash
# 查看服务状态与启动日志
docker compose ps
docker compose logs -f auth

# 更新代码后重建认证服务
docker compose up -d --build auth

# 仅停止服务，保留 MySQL、Redis 与签名私钥数据卷
docker compose down
```

生产环境应把认证服务放在 HTTPS 反向代理之后，设置 `auth.cookie.secure: true`，并确保 `oauth.issuer` 与外部访问根地址完全一致。反向代理需保留 `Host` 并传递 `X-Forwarded-Proto`；`auth.cors.allowed-origins` 必须包含浏览器实际访问的完整 Origin（例如 `https://auth.nekocore.cn`），否则登录 POST 会被 Spring 以 `Invalid CORS request` 拒绝。源站默认关闭 HTTP 压缩，由 CDN 或反向代理统一启用 gzip/brotli；不要在源站与 CDN 同时压缩，否则部分 CDN 会保留未压缩的 `Content-Length`，导致浏览器报 `ERR_HTTP2_PROTOCOL_ERROR` 并白屏。

不要用 `docker compose down -v` 做常规重启，它会删除数据库、Redis AOF 和 OAuth 签名私钥。若确实需要完全重建环境，请先备份并确认不再需要现有账户、令牌与客户端配置。

## 配置

生产配置以 `config/application.yml` 为主，默认从 `/run/secrets/` 读取 Docker secrets；本地开发启用 `local` profile，由 `config/application-local.yml` 覆盖数据库、Redis 地址与密钥目录。`config/secrets/` 通过 Spring `configtree` 导入，文件名即配置键名。

| YAML 配置项 | 内置默认值 | 说明 |
| --- | --- | --- |
| `server.port` | `65534` | HTTP 端口 |
| `server.compression.enabled` | `false` | 源站 HTTP 压缩；CDN 已启用压缩时保持关闭 |
| `spring.datasource.url` | H2 文件数据库 | Compose 示例为 MySQL 8 JDBC 地址 |
| `spring.datasource.username` | `sa` | Compose 示例为 `nekoauth` |
| `spring.datasource.password` | 空 | Compose 示例读取 `${db_password}` |
| `spring.data.redis.host` | 未设置 | 设置后启用 Redis 共享登录限流 |
| `spring.data.redis.port` | `6379` | Redis 端口 |
| `spring.data.redis.database` | `0` | Redis 数据库编号 |
| `spring.data.redis.password` | 空 | Redis 密码；未启用认证时留空 |
| `auth.setup.enabled` | `true` | 关闭后安装入口直接返回 404；生产设为 `false` |
| `auth.setup.state-dir` | `./data` | 安装配置与完成标记目录 |
| `auth.setup.token` | 自动生成 | 可选安装向导的一次性令牌 |
| `auth.jwt.secret` | 开发默认值 | JWT HMAC 密钥；生产必须使用至少 32 字节随机值 |
| `auth.jwt.issuer` | `neko-auth` | JWT issuer |
| `auth.jwt.access-ttl` | `15m` | 访问令牌有效期 |
| `auth.jwt.refresh-ttl` | `14d` | 刷新令牌有效期 |
| `auth.cookie.secure` | `false` | HTTPS 部署时设为 `true` |
| `auth.cookie.same-site` | `Strict` | 刷新 Cookie 的 SameSite |
| `auth.cors.allowed-origins` | `http://localhost:5173`, `https://auth.nekocore.cn` | 允许的浏览器 Origin；生产必须包含实际访问域名，可用 YAML 列表配置多个 |
| `auth.rate-limit.attempts` | `20` | 单 IP 限流窗口内失败次数 |
| `auth.rate-limit.window` | `5m` | 登录限流窗口 |
| `auth.bootstrap-admin.username` | 空 | 首次启动创建管理员的用户名；需与邮箱、密码同时设置 |
| `auth.bootstrap-admin.email` | 空 | 首次启动创建管理员的邮箱 |
| `auth.bootstrap-admin.display-name` | `Neko Admin` | 初始管理员昵称 |
| `auth.bootstrap-admin.password` | 空 | 初始管理员密码；长度 6-30 个字符 |
| `oauth.issuer` | `http://localhost:65534` | OAuth/OIDC issuer 与元数据根地址；公网部署必须是 HTTPS |
| `oauth.key-file` | `./data/oauth-signing-key.pem` | RS256 签名私钥路径；Compose 下位于数据卷 |
| `oauth.authorization-code-ttl` | `5m` | 授权码有效期 |
| `oauth.access-token-ttl` | `1h` | OAuth 访问令牌有效期 |
| `oauth.id-token-ttl` | `10m` | OIDC ID Token 有效期 |
| `oauth.refresh-token-ttl` | `30d` | OAuth 刷新令牌有效期 |

管理员初始化只在该用户名和邮箱都不存在时执行，不会在后续重启时重置密码或角色。

## API

访问令牌通过 `Authorization: Bearer <token>` 传递，刷新令牌只通过 Cookie 传递。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/setup/status` | 可选安装状态；安装完成或禁用后返回 404 |
| `POST` | `/api/setup/install` | 校验并保存安装配置；需要 `X-Setup-Token` |
| `POST` | `/api/auth/register` | 注册并登录；新客户端使用 `nickname/email/password`，兼容旧 `username/displayName` |
| `POST` | `/api/auth/login` | 用户名或邮箱登录 |
| `POST` | `/api/auth/refresh` | 轮换刷新令牌并签发新访问令牌 |
| `POST` | `/api/auth/logout` | 退出当前会话 |
| `GET` | `/api/auth/me` | 当前用户 |
| `PATCH` | `/api/auth/me` | 修改昵称 |
| `POST` | `/api/auth/password` | 修改密码并撤销全部会话 |
| `GET` | `/api/auth/sessions` | 活跃设备会话 |
| `DELETE` | `/api/auth/sessions/{id}` | 撤销指定会话 |
| `DELETE` | `/api/auth/sessions` | 撤销全部会话 |
| `GET` | `/api/admin/users` | 管理员查询用户，支持 `query/page/size` |
| `PATCH` | `/api/admin/users/{id}` | 管理员调整角色或启停账户 |
| `GET` | `/api/admin/oauth-clients` | 管理员列出 OAuth 应用 |
| `POST` | `/api/admin/oauth-clients` | 登记 OAuth 应用并一次性返回客户端密钥 |
| `PATCH` | `/api/admin/oauth-clients/{id}` | 修改回调、scope、启用状态或客户端类型 |
| `POST` | `/api/admin/oauth-clients/{id}/secret` | 轮换机密客户端密钥 |

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
  "nickname": "星野小梦",
  "email": "neko@example.com",
  "password": "secret123"
}
```

- `nickname` 长度 1-20，允许重复；`email` 长度不超过 100、统一转小写且必须唯一；密码长度 6-30，不强制字母与数字组合。
- 新注册用户的内部 `username` 由服务端生成并保持唯一，API 响应同时返回 `username`、`nickname` 与 `displayName`。旧客户端继续可显式提交 `username`。
- `verificationCode` 字段可以被接收以兼容 NekoMusic 注册载荷，但 NekoAuth 当前没有邮件发信和验证码存储流程，因此不会校验它。不要仅凭该字段把邮箱视为已验证。

### 用户 ID 与 NekoMusic 迁移

- Flyway `V4` 将新用户自增起点设为 `1000000`，已存在的用户 ID 不变；`refresh_tokens`、OAuth 授权码与 consent 的外键不会被重排。
- 用户密码规则对齐 NekoMusic：`Argon2Factory.create()` 的 Argon2i、`t=10`、`m=65536 KiB`、`p=1`。NekoMusic 的 `$argon2i$...` 哈希可以原样迁移到 `users.password_hash`。
- 旧 NekoAuth 用户的 BCrypt(12) 哈希仍可登录；登录成功后会自动改写为当前 Argon2i 参数。OAuth 客户端密钥仍独立使用 BCrypt(12)，不受用户密码算法影响。
- 迁移 NekoMusic 用户时建议映射：`id -> id`、`nickname -> display_name`、`email -> email`、`password -> password_hash`。为满足 NekoAuth 唯一用户名约束，可暂时生成 `CONCAT('neko_', id)` 作为 `username`。
- 导入前必须检查 NekoMusic 的旧 ID 是否与当前 NekoAuth 的小于 `1000000` 的历史用户 ID 冲突。若冲突，应先设计一次性 ID 映射并同步处理所有外键，不能直接覆盖或重排线上用户。

MySQL/MariaDB 可先将 NekoMusic 用户复制到临时表 `nekomusic_users`，审计 ID 冲突后执行相同字段的 `INSERT ... SELECT`。显式写入更大 ID 后，MySQL/MariaDB 会自动把下一个自增值提升到最大 ID 之后；PostgreSQL 则还需要同步 identity sequence。

## 安全设计

- Compose 默认关闭安装入口；可选安装向导在完成后同样由 Servlet 过滤器直接返回 404，不依赖前端隐藏。
- 安装和服务连接过程不会把 MySQL/MariaDB、Redis 或管理员密码写入日志。
- 安装文件以临时文件 + 原子移动写入，使用 POSIX `0600` 权限；完成标记使用独占创建。
- 首次启动可由 `config/application.yml` 创建 ADMIN，密码只保存 Argon2i 哈希，后续重启不会覆盖已有账户。
- 用户密码使用 NekoMusic 同款 Argon2i 参数；密码长度 6-30 个字符。旧 BCrypt 用户只在成功登录后升级，避免迁移前强制重置。
- 新用户 ID 从 `1000000` 开始，避免与后续导入的 NekoMusic 旧 ID 段混用；OAuth 客户端密钥继续使用 BCrypt cost 12。
- JWT 仅存 JS 内存。刷新令牌不暴露给 JavaScript，数据库只保存其 SHA-256 哈希。
- 每次刷新都会撤销旧刷新令牌；已轮换令牌再次出现时，撤销该用户在同一 OAuth 客户端下的全部活动刷新令牌。
- 用户 `tokenVersion` 使密码修改、角色变化或停用立即让旧 JWT 失效。
- API 使用无状态安全上下文；Cookie 刷新端点配合 `SameSite=Strict` 和受限 CORS。
- CSP 禁止内联脚本，并禁用 iframe 嵌入；HSTS 在 HTTPS 下由 Spring Security 输出。
- 公共客户端强制 PKCE S256；授权码短期有效、只保存哈希且只能交换一次。
- 回调地址精确匹配，非回环地址禁止明文 HTTP；OAuth 协议错误与业务 API 错误分别保持标准响应格式。
- OAuth 访问令牌仅在 `profile` scope 下包含用户名、仅在 `roles` scope 下包含角色；RS256 私钥落盘到受限数据目录，JWKS 仅暴露公钥。

生产环境至少必须：使用 HTTPS、设置 `auth.cookie.secure: true`、保持 `auth.setup.enabled: false`、使用独立数据库账号、备份 `nekoauth-data`，并在反向代理层增加更严格的限流与审计。

## 测试

```bash
./mvnw test
cd frontend && npm run build
```

后端测试覆盖认证主流程、JWT 鉴权、刷新轮换、OAuth 刷新令牌重放与授权链撤销、scope 最小化、`user:email` 别名、UserInfo 声明、ASCII Bearer 错误头、登出、密码变更、会话撤销、管理员权限隔离、OAuth 公共/机密客户端、PKCE、回调校验、停用客户端、安装入口 404 生命周期与安装配置覆盖顺序。认证测试还覆盖 `1000000` 起始 ID、NekoMusic 注册字段、Argon2i 固定哈希、旧 BCrypt 自动升级、重复昵称与根路径/后台页面 404。

## 目录

```text
src/main/java/neko/fantasy/auth/          后端应用、领域模型、服务、安全与 API
src/main/java/neko/fantasy/auth/setup/    一次性安装流程、连接校验、配置落盘与 404 过滤器
src/main/resources/db/migration/          按数据库厂商拆分的 Flyway 迁移
src/main/resources/META-INF/spring.factories  安装配置高优先级加载器
src/test/                                 集成测试与安装测试
frontend/src/                             Vue 页面、组件、API 客户端与状态
frontend/src/assets/catgirl/              梦幻网络猫娘主题 WebP 插画
scripts/build-frontend.sh                 Vite 前端构建到 src/main/resources/site
scripts/build.sh                          前端构建 + 后端 jar 打包
Dockerfile / compose.yml                  生产镜像与 MySQL、Redis 编排
compose.dev.yml                           本地开发时映射 MySQL 与 Redis 回环端口
```
