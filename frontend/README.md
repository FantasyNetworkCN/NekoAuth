# NekoAuth Frontend

Vue 3 + TypeScript + Vite frontend for NekoAuth.

## Development

```bash
npm ci
npm run dev
```

Vite proxies `/api`, `/oauth2`, and `/.well-known` to
`https://auth.nekocore.cn` by default. The proxy rewrites `Origin` and `Referer`
to the target origin so the deployed CORS policy accepts requests from local
development URLs. Override the proxy target with `VITE_DEV_PROXY_TARGET`, or set
`VITE_API_BASE_URL` when the frontend should request another API origin
directly.

## Build

```bash
npm run build
```

Vite writes the production build to `../src/main/resources/site`. The root
`scripts/build.sh` builds this frontend through Maven and packages it into the
Spring Boot executable jar.
