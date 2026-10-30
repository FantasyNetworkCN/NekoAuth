# NekoAuth

NekoAuth 是 FantasyNetwork 梦幻网络的账户与 OAuth 2.0 / OpenID Connect 认证服务，包含 Spring Boot 后端和 Vue 3 前端。默认部署使用 MySQL 8 与 Redis 7，管理员由 `config/application.yml` 在首次启动时创建；生产 Compose 会关闭 `/setup` 安装入口。

## 功能

- 注册、用户名/邮箱登录、安全退出
- 15 分钟 JWT 访问令牌，浏览器内存存储，不写入 localStorage
- 14 天刷新令牌，`HttpOnly + SameSite` Cookie 保存
- 刷新令牌轮换、SHA-256 哈希落库、重放检测与授权链撤销
- 修改密码后立即失效所有旧访问令牌与刷新会话
- API 支持设备会话管理、密码修改与全部会话退出（网页端不提供个人资料页）
- 管理员用户列表、角色调整、账户启停
- BCrypt(12) 密码哈希、Redis 共享登录限流、统一错误响应、CSP/HSTS 安全头
- Flyway 数据库迁移，支持 H2、PostgreSQL、MySQL、MariaDB
- 猫娘主题的登录、注册、OAuth 授权与管理员猫屋；包含身份卡主视觉、守护者插画和移动端横幅
- 管理员后台仅通过 `/admin` 访问，未登录会跳转登录页，非管理员无法进入；`/app` 已移除并返回 404
- OAuth 2.0 授权码 + PKCE S256，OIDC 发现、JWKS、UserInfo、自省与撤销
- OAuth 访问令牌按 scope 最小化声明；管理员 OAuth 应用猫屋支持回调地址、scope、公共/机密客户端与密钥轮换
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

`config/secrets/db_password` 会同时供 Spring 数据源和 MySQL 初始化使用；`config/secrets/admin_password` 必须包含字母和数字，长度为 8-64 个字符。Compose 会把四份文件挂载为 `/run/secrets` 下的 Docker secrets，应用容器以非 root 用户运行也能读取，不依赖宿主机文件的 UID 与 `600` 权限。`config/application.yml` 与 `config/secrets/` 默认已被 Git 和 Docker 构建上下文忽略。

启动整套服务：

```bash
docker compose up -d --build
docker compose logs -f auth
```

打开 `http://<服务器>:65534/login` 即可使用初始管理员登录。首次启动会执行 Flyway 迁移，并在用户名和邮箱都不存在时创建 `auth.bootstrap-admin` 指定的 ADMIN；之后修改配置不会覆盖已有账户。

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

NekoAuth 同时是 FantasyNetwork 的 OAuth 2.0 / OpenID Connect 授权服务。管理员在 `/admin/clients` 登记应用与精确回调地址；普通成员通过 `/authorize` 查看并确认公开的权限范围。

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
| `auth.bootstrap-admin.password` | 空 | 初始管理员密码；需包含字母和数字，8-64 个字符 |
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
| `POST` | `/api/auth/register` | 注册并登录 |
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
  "fields": { "password": "密码长度需为 8-64 个字符" }
}
```

## 安全设计

- Compose 默认关闭安装入口；可选安装向导在完成后同样由 Servlet 过滤器直接返回 404，不依赖前端隐藏。
- 安装和服务连接过程不会把 MySQL/MariaDB、Redis 或管理员密码写入日志。
- 安装文件以临时文件 + 原子移动写入，使用 POSIX `0600` 权限；完成标记使用独占创建。
- 首次启动可由 `config/application.yml` 创建 ADMIN，密码只保存 BCrypt 哈希，后续重启不会覆盖已有账户。
- 密码使用 BCrypt cost 12；密码同时要求字母、数字，并限制 UTF-8 长度不超过 BCrypt 的 72 字节边界。
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

后端测试覆盖认证主流程、JWT 鉴权、刷新轮换、OAuth 刷新令牌重放与授权链撤销、scope 最小化、登出、密码变更、会话撤销、管理员权限隔离、OAuth 公共/机密客户端、PKCE、回调校验、停用客户端、安装入口 404 生命周期与安装配置覆盖顺序。

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
