import { fileURLToPath, URL } from 'node:url'

import { defineConfig, type ProxyOptions } from 'vite'
import vue from '@vitejs/plugin-vue'

const apiTarget = process.env.VITE_DEV_PROXY_TARGET || 'https://auth.nekocore.cn'
const apiProxy: ProxyOptions = {
  target: apiTarget,
  changeOrigin: true,
  secure: true,
  configure(proxy) {
    proxy.on('proxyReq', (proxyRequest) => {
      proxyRequest.setHeader('origin', apiTarget)
      proxyRequest.setHeader('referer', `${apiTarget}/`)
    })
  },
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    outDir: '../src/main/resources/site',
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: {
      '/api': apiProxy,
      '/oauth2': apiProxy,
      '/.well-known': apiProxy,
    },
  },
})
