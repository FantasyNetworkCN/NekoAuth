# NekoAuth Frontend

Vue 3 + TypeScript + Vite frontend for NekoAuth.

## Development

```bash
npm ci
npm run dev
```

Vite proxies `/api` to `http://localhost:65534`. Override the API origin with
`VITE_API_BASE_URL` when the backend is hosted elsewhere.

## Build

```bash
npm run build
```

Vite writes the production build to `../src/main/resources/site`. The root
`scripts/build.sh` builds this frontend through Maven and packages it into the
Spring Boot executable jar.
