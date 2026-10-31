import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

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
      '/api': {
        target: 'https://auth.nekocore.cn',
        changeOrigin: true,
      },
      '/oauth2': {
        target: 'https://auth.nekocore.cn',
        changeOrigin: true,
      },
      '/.well-known': {
        target: 'https://auth.nekocore.cn',
        changeOrigin: true,
      },
    },
  },
})
