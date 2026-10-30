import './assets/main.css'

import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { auth } from './stores/auth'

async function start() {
  auth.setExpiredHandler(() => {
    if (router.currentRoute.value.meta.requiresAuth) {
      void router.push({ name: 'login', query: { reason: 'expired' } })
    }
  })

  await auth.bootstrap()
  createApp(App).use(router).mount('#app')
}

void start()
