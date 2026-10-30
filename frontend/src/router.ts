import { createRouter, createWebHistory } from 'vue-router'
import { auth } from '@/stores/auth'
import AppShell from '@/components/AppShell.vue'
import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'
import AdminView from '@/views/AdminView.vue'
import OAuthClientsView from '@/views/OAuthClientsView.vue'
import OAuthConsentView from '@/views/OAuthConsentView.vue'
import NotFoundView from '@/views/NotFoundView.vue'
import SetupView from '@/views/SetupView.vue'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    {
      path: '/setup',
      name: 'setup',
      component: SetupView,
    },
    {
      path: '/login',
      alias: '/',
      name: 'login',
      component: LoginView,
      meta: { guestOnly: true },
    },
    {
      path: '/register',
      name: 'register',
      component: RegisterView,
      meta: { guestOnly: true },
    },
    {
      path: '/authorize',
      alias: '/oauth/consent',
      name: 'oauth-consent',
      component: OAuthConsentView,
      meta: { requiresAuth: true },
    },
    {
      path: '/admin',
      component: AppShell,
      meta: { requiresAuth: true, requiresAdmin: true },
      children: [
        { path: '', name: 'admin', component: AdminView },
        { path: 'clients', name: 'oauth-clients', component: OAuthClientsView },
      ],
    },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFoundView },
  ],
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth && !auth.isAuthenticated.value) {
    const promptValue = Array.isArray(to.query.prompt) ? to.query.prompt[0] : to.query.prompt
    const prompt = typeof promptValue === 'string' ? promptValue.split(/\s+/) : []
    if (to.name === 'oauth-consent' && prompt.includes('none')) {
      return true
    }
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guestOnly && auth.isAuthenticated.value) {
    const redirect = to.query.redirect
    if (typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')) {
      return redirect
    }
    return { name: 'admin' }
  }
  if (to.meta.requiresAdmin && !auth.isAdmin.value) {
    return { name: 'login', query: { reason: 'forbidden' } }
  }
  return true
})

export default router
