# NekoAuth

NekoAuth 是一套可直接运行的账户认证服务，包含 Spring Boot 后端和 Vue 3 前端。首次启动通过一次性 `/setup` 页面配置管理员、MySQL/MariaDB 与 Redis；安装完成后 `/setup` 与 `/api/setup/**` 永久返回真实 HTTP 404。

## 功能

- 注册、用户名/邮箱登录、安全退出
- 一次性安装向导：管理员账户、MySQL/MariaDB、Redis、连接校验与配置落盘
- 安装完成后在 Servlet 层关闭安装入口并返回 404，配置保存在本机 `data/` 目录
- 15 分钟 JWT 访问令牌，浏览器内存存储，不写入 localStorage
- 14 天刷新令牌，`HttpOnly + SameSite` Cookie 保存
- 刷新令牌轮换、SHA-256 哈希落库、重放检测与全会话撤销
- 修改密码后立即失效所有旧访问令牌与刷新会话
- API 支持设备会话管理、密码修改与全部会话退出（网页端不提供个人资料页）
- 管理员用户列表、角色调整、账户启停
- BCrypt(12) 密码哈希、Redis 共享登录限流、统一错误响应、CSP/HSTS 安全头
- Flyway 数据库迁移，支持 H2、PostgreSQL、MySQL、MariaDB
- 响应式登录、注册、安装和管理员页面
- 管理员后台仅通过 `/admin` 访问，未登录会跳转登录页，非管理员无法进入；`/app` 已移除并返回 404
- OAuth 2.0 授权码 + PKCE S256，OIDC 发现、JWKS、UserInfo、自省与撤销
- 刷新令牌轮换与复用检测；管理员 OAuth 应用猫屋：回调地址、scope、公共/机密客户端与密钥轮换

## 技术栈

- Java 21 + Spring Boot 4.1
- Spring Security 7、Spring Data JPA、Flyway
- MySQL 8 / MariaDB 10.6+、Redis 7、H2 / PostgreSQL
- JJWT 0.13、Vue 3.5、TypeScript、Vite、Vue Router

## 首次安装

NekoAuth 启动时先使用本地 H2 完成安装引导。打开 `/setup` 后填写一次性安装令牌、管理员账户、MySQL/MariaDB 与 Redis 连接信息；后端会自动识别 MySQL 或 MariaDB，实际连接校验后再以原子方式写入：

```text
data/nekoauth-setup.yml   管理员、MySQL/MariaDB、Redis 与随机 JWT 密钥
data/.setup-complete      安装完成标记
```

两个文件默认设置为仅当前系统用户可读写，且 `data/` 已在 Git 忽略列表中。

安装令牌来源：

- 未设置 `AUTH_SETUP_TOKEN` 时，服务每次启动会生成一次性随机令牌并打印到日志。
- 也可以通过环境变量预先指定令牌；安装完成后该令牌不再有效。

提交安装后必须重启服务。重启时应用会以高优先级加载安装配置，连接 MySQL/MariaDB、执行 Flyway 迁移、创建首个 ADMIN，并切换到 Redis 登录限流。重启后：

- `/setup` 返回 HTTP 404。
- `/api/setup/status`、`/api/setup/install` 返回 HTTP 404。
- `/setup` 不会回退到 SPA 页面。

数据库支持 MySQL 8 与 MariaDB 10.6+。安装时会自动识别服务端类型并写入正确的 JDBC URL 与驱动；旧版生成的 `jdbc:mysql://` 安装配置如果实际连接的是 MariaDB，也会在启动时自动切换为 MariaDB 驱动。

## 本地开发

要求 Java 21+、Node.js 22.18+ 或 24.12+。

```bash
# 终端 1：后端，默认监听 65534
./mvnw spring-boot:run

# 终端 2：前端，默认监听 5173，并将 /api 代理到后端
cd frontend
npm ci
npm run dev
```

访问 `http://localhost:5173/setup`。安装页默认主机名面向 Compose；本机运行时请将 MySQL/MariaDB 与 Redis 主机改为 `127.0.0.1`。

需要本机依赖服务时可以先启动容器：

```bash
cp .env.example .env
docker compose up -d mysql redis
```

## 生产构建

生成包含前端的单一可执行 jar：

```bash
./scripts/build.sh
java -jar target/NekoAuth-1.0.0.jar
```

访问 `http://localhost:65534/setup` 完成首次安装，再重启 jar 进程。Maven 在 `generate-resources` 阶段调用 `scripts/build-frontend.sh`，Vite 将产物直接写入 `src/main/resources/site`，随后被打入 jar。只构建后端时可加 `-Dneko.frontend.skip=true`。

静态资源缓存策略：`/assets/**` 使用 Vite 文件指纹并返回 `public, max-age=31536000, immutable`；入口 HTML、SPA 路由、安装页面和全部 `/api/**` 响应返回 `Cache-Control: no-store`，避免 CDN 缓存登录页或敏感数据。

## OAuth 2.0 与 OIDC

NekoAuth 同时是 FantasyNetwork 的 OAuth 2.0 / OpenID Connect 授权服务。管理员在 `/admin/clients` 登记应用与精确回调地址；普通成员通过 `/authorize` 查看并确认公开的权限范围。

`AUTH_OAUTH_ISSUER` 会写入发现文档、ID Token 的 `iss` 与端点地址，必须是客户端实际访问的公网根地址（例如 `https://auth.fantasy.network`，末尾不要带 `/`）。反向代理需要保留 `Host` 并传递 `X-Forwarded-Proto`。

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

公共客户端必须使用 PKCE `S256`，且不能持有客户端密钥；机密客户端可通过 HTTP Basic 或表单参数提交密钥。回调地址必须与登记值完全一致；非本机回调地址只允许 `https://`，明文 `http://` 仅可用于 `localhost`、`127.0.0.1` 或 `[::1]` 本机开发。

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

授权码只能使用一次；刷新令牌每次使用都会轮换，旧令牌再次出现时会撤销对应授权链。RS256 签名私钥首次启动时生成并持久化到 `AUTH_OAUTH_KEY_FILE`（Compose 默认数据卷中的 `data/oauth-signing-key.pem`），请与数据库一起备份并限制读取权限。

## Docker Compose

```bash
cp .env.example .env
# 修改 .env 中的 MySQL 密码、公网 AUTH_OAUTH_ISSUER 和 Cookie 安全设置
docker compose up -d --build

# 获取一次性安装令牌
docker compose logs auth
```

打开 `http://<服务器>:65534/setup` 并填写：

| 项目 | Compose 默认值 |
| --- | --- |
| MySQL/MariaDB 主机 | `mysql` |
| MySQL/MariaDB 端口 | `3306` |
| MySQL/MariaDB 数据库 | `nekoauth` |
| MySQL/MariaDB 用户名 | `nekoauth` |
| MySQL/MariaDB 密码 | `.env` 中的 `DB_PASSWORD` |
| Redis 主机 | `redis` |
| Redis 端口 | `6379` |
| Redis 密码 | 空 |
| Redis 数据库编号 | `0` |

配置保存后执行：

```bash
docker compose restart auth
```

`nekoauth-data` 卷保存安装配置与 OAuth RS256 签名私钥；`mysql-data` 与 `redis-data` 保存业务数据。若需要重新安装，请先确认没有业务数据，再删除 `nekoauth-data` 卷中的 `nekoauth-setup.yml` 与 `.setup-complete` 后重启。

若通过 HTTPS 反向代理访问，请设置 `AUTH_COOKIE_SECURE=true`，并把 `AUTH_OAUTH_ISSUER` 设置为该公网 HTTPS 根地址。

## 配置

安装生成配置优先于应用内置默认值，但环境变量仍可覆盖安装文件。

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `PORT` | `65534` | HTTP 端口 |
| `AUTH_SETUP_STATE_DIR` | `./data` | 安装配置与完成标记目录 |
| `AUTH_SETUP_TOKEN` | 自动生成 | 一次性安装令牌；为空时打印到启动日志 |
| `DB_URL` | H2 文件数据库 | 可选，直接覆盖 JDBC 连接串 |
| `DB_USERNAME` | `sa` | 可选，覆盖数据库用户名 |
| `DB_PASSWORD` | 空 | 可选，覆盖数据库密码 |
| `AUTH_JWT_SECRET` | 安装时随机生成 | 可选，覆盖 JWT HMAC 密钥 |
| `AUTH_JWT_ISSUER` | `neko-auth` | JWT issuer |
| `AUTH_ACCESS_TTL` | `15m` | 访问令牌有效期 |
| `AUTH_REFRESH_TTL` | `14d` | 刷新令牌有效期 |
| `AUTH_COOKIE_SECURE` | `false` | HTTPS 部署时应设为 `true` |
| `AUTH_COOKIE_SAME_SITE` | `Strict` | 刷新 Cookie 的 SameSite |
| `AUTH_ALLOWED_ORIGINS` | `http://localhost:5173` | 允许跨域的 API 来源，逗号分隔 |
| `AUTH_LOGIN_ATTEMPTS` | `20` | 单 IP 限流窗口内失败次数 |
| `AUTH_LOGIN_WINDOW` | `5m` | 登录限流窗口 |
| `AUTH_ADMIN_USERNAME` | 空 | 可选，启动时直接引导管理员 |
| `AUTH_ADMIN_EMAIL` | 空 | 可选，启动时直接引导管理员 |
| `AUTH_ADMIN_PASSWORD` | 空 | 可选，启动时直接引导管理员 |
| `AUTH_OAUTH_ISSUER` | `http://localhost:65534` | OAuth/OIDC issuer 与元数据根地址；公网部署必须是 HTTPS |
| `AUTH_OAUTH_KEY_FILE` | `${AUTH_SETUP_STATE_DIR}/oauth-signing-key.pem` | RS256 签名私钥路径；Compose 下位于数据卷 |
| `AUTH_OAUTH_CODE_TTL` | `5m` | 授权码有效期 |
| `AUTH_OAUTH_ACCESS_TTL` | `1h` | OAuth 访问令牌有效期 |
| `AUTH_OAUTH_ID_TTL` | `10m` | OIDC ID Token 有效期 |
| `AUTH_OAUTH_REFRESH_TTL` | `30d` | OAuth 刷新令牌有效期 |

Redis 连接使用 Spring Boot 标准键 `spring.data.redis.*`。安装向导写入的 YAML 已包含 `host`、`port`、`database`、`timeout` 与可选密码。

## API

访问令牌通过 `Authorization: Bearer <token>` 传递，刷新令牌只通过 Cookie 传递。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/setup/status` | 安装状态；完成安装后返回 404 |
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

- 安装入口需要一次性令牌；安装保存后由 Servlet 过滤器直接返回 404，不依赖前端隐藏。
- 安装和服务连接过程不会把 MySQL/MariaDB、Redis 或管理员密码写入日志。
- 安装文件以临时文件 + 原子移动写入，使用 POSIX `0600` 权限；完成标记使用独占创建。
- 安装完成后由启动引导自动创建 ADMIN，密码只保存 BCrypt 哈希。
- 密码使用 BCrypt cost 12；密码同时要求字母、数字，并限制 UTF-8 长度不超过 BCrypt 的 72 字节边界。
- JWT 仅存 JS 内存。刷新令牌不暴露给 JavaScript，数据库只保存其 SHA-256 哈希。
- 每次刷新都会撤销旧刷新令牌；已撤销令牌再次出现时，撤销该用户全部刷新会话。
- 用户 `tokenVersion` 使密码修改、角色变化或停用立即让旧 JWT 失效。
- API 使用无状态安全上下文；Cookie 刷新端点配合 `SameSite=Strict` 和受限 CORS。
- CSP 禁止内联脚本，并禁用 iframe 嵌入；HSTS 在 HTTPS 下由 Spring Security 输出。
- 公共客户端强制 PKCE S256；授权码短期有效、只保存哈希且只能交换一次。
- 回调地址精确匹配，非回环地址禁止明文 HTTP；OAuth 协议错误与业务 API 错误分别保持标准响应格式。
- 刷新令牌轮换并检测复用；RS256 私钥落盘到受限数据目录，JWKS 仅暴露公钥。

生产环境至少必须：使用 HTTPS、设置 `AUTH_COOKIE_SECURE=true`、使用独立数据库账号、限制 `/setup` 首次安装时的网络暴露，并在反向代理层增加更严格的限流与审计。

## 测试

```bash
./mvnw test
cd frontend && npm run build
```

后端测试覆盖认证主流程、JWT 鉴权、刷新轮换、令牌重放、登出、密码变更、会话撤销、管理员权限隔离、OAuth 公共/机密客户端、PKCE、回调校验、停用客户端、安装入口 404 生命周期与安装配置覆盖顺序。

## 目录

```text
src/main/java/neko/fantasy/auth/          后端应用、领域模型、服务、安全与 API
src/main/java/neko/fantasy/auth/setup/    一次性安装流程、连接校验、配置落盘与 404 过滤器
src/main/resources/db/migration/          按数据库厂商拆分的 Flyway 迁移
src/main/resources/META-INF/spring.factories  安装配置高优先级加载器
src/test/                                 集成测试与安装测试
frontend/src/                             Vue 页面、组件、API 客户端与状态
scripts/build-frontend.sh                 Vite 前端构建到 src/main/resources/site
scripts/build.sh                          前端构建 + 后端 jar 打包
Dockerfile / compose.yml                  生产镜像与 MySQL、Redis 编排
```
