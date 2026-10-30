<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AuthLayout from '@/components/AuthLayout.vue'
import AppIcon from '@/components/AppIcon.vue'
import { ApiError } from '@/lib/api'
import { auth } from '@/stores/auth'
import { showToast } from '@/lib/toast'

const route = useRoute()
const router = useRouter()
const loginHint = route.query.login_hint
const identifier = ref(typeof loginHint === 'string' ? loginHint : '')
const password = ref('')
const showPassword = ref(false)
const loading = ref(false)
const loggingOut = ref(false)
const error = ref('')
const loginSuccess = ref('')

const redirectTarget = computed(() => {
  const value = route.query.redirect
  return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//') ? value : null
})
const signedInWithoutRedirect = computed(() => !redirectTarget.value && auth.isAuthenticated.value)
const isOAuthFlow = computed(() => redirectTarget.value?.startsWith('/authorize') === true)
const registerLink = computed(() => ({
  name: 'register',
  query: redirectTarget.value ? { redirect: redirectTarget.value } : {},
}))

const notice = computed(() => {
  if (route.query.reason === 'expired') return '猫娘小梦打了个盹，登录状态过期了。请重新登录喵。'
  if (route.query.reason === 'registered') return '账户已经织入梦幻网络，可以登录啦。'
  if (route.query.reason === 'password-changed') return '密码已更新，所有旧会话都被猫爪拍散了，请重新登录。'
  return ''
})

async function submit() {
  if (loading.value) return
  error.value = ''
  loading.value = true
  try {
    await auth.login(identifier.value.trim(), password.value)
    const name = auth.state.user?.displayName || auth.state.user?.username
    if (redirectTarget.value) {
      showToast(`欢迎回来，${name}`, 'success')
      await router.replace(redirectTarget.value)
      return
    }
    loginSuccess.value = `登录成功，${name}。NekoAuth 仅提供 API 与 OAuth 授权，请从发起登录的应用继续。`
    showToast(`欢迎回来，${name}`, 'success')
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : '网络异常，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function logout() {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    await auth.logout()
    await router.replace({ name: 'login' })
  } finally {
    loggingOut.value = false
  }
}
</script>

<template>
  <AuthLayout
    eyebrow="Neko Identity Gate"
    :title="isOAuthFlow ? '确认身份，继续冒险' : '欢迎回到梦幻网络'"
    :subtitle="isOAuthFlow
      ? '小梦正在把你的身份交给发起授权的应用。登录后就能看到它申请的权限喵。'
      : '使用梦幻网络通行证登录。NekoAuth 不提供用户后台，登录后请回到发起授权的应用。'"
  >
    <div v-if="signedInWithoutRedirect" class="notice-banner">
      <AppIcon name="check" :size="17" />
      <span>当前已登录为 {{ auth.state.user?.displayName || auth.state.user?.username }}。此服务只提供 API 与 OAuth 授权，不提供用户后台。</span>
      <button class="btn btn-ghost btn-small notice-action" type="button" :disabled="loggingOut" @click="logout">
        退出账户
      </button>
    </div>

    <div v-if="loginSuccess" class="notice-banner">
      <AppIcon name="check" :size="17" />
      <span>{{ loginSuccess }}</span>
    </div>

    <div v-if="notice" class="notice-banner">
      <AppIcon name="alert" :size="17" />
      <span>{{ notice }}</span>
    </div>

    <div v-if="error" class="alert alert-error" role="alert">
      <AppIcon name="alert" :size="18" />
      <span>{{ error }}</span>
    </div>

    <form class="auth-form" novalidate @submit.prevent="submit">
      <label class="field">
        <span class="field-label">邮箱或用户名</span>
        <span class="input-shell" :class="{ 'has-error': error }">
          <AppIcon name="user" :size="18" />
          <input
            v-model="identifier"
            name="identifier"
            type="text"
            autocomplete="username"
            placeholder="neko@fantasy.network 或 neko"
            maxlength="254"
            required
            autofocus
          />
        </span>
      </label>

      <label class="field">
        <span class="field-row">
          <span class="field-label">通行证密码</span>
          <span class="field-hint">区分大小写喵</span>
        </span>
        <span class="input-shell" :class="{ 'has-error': error }">
          <AppIcon name="lock" :size="18" />
          <input
            v-model="password"
            name="password"
            :type="showPassword ? 'text' : 'password'"
            autocomplete="current-password"
            placeholder="输入你的秘密咒语"
            maxlength="128"
            required
          />
          <button
            class="input-action"
            type="button"
            :aria-label="showPassword ? '隐藏密码' : '显示密码'"
            @click="showPassword = !showPassword"
          >
            <AppIcon :name="showPassword ? 'eye-off' : 'eye'" :size="18" />
          </button>
        </span>
      </label>

      <div class="form-meta">
        <span><span class="mini-dot"></span> 小梦会记住你最多 14 天</span>
        <RouterLink class="text-link" :to="registerLink">第一次来？</RouterLink>
      </div>

      <button class="btn btn-primary btn-block" type="submit" :disabled="loading || !identifier || !password">
        <span v-if="loading" class="spinner"></span>
        <template v-else>
          <AppIcon name="paw" :size="17" />
          进入梦幻网络
          <AppIcon name="arrow-right" :size="18" />
        </template>
      </button>
    </form>

    <div class="auth-switch">
      还没有通行证？
      <RouterLink :to="registerLink">和猫娘签订契约</RouterLink>
    </div>

    <div class="trust-strip">
      <span><AppIcon name="lock" :size="14" /> HttpOnly</span>
      <span><AppIcon name="refresh" :size="14" /> 令牌轮换</span>
      <span><AppIcon name="shield-cat" :size="14" /> 猫爪防护</span>
    </div>
  </AuthLayout>
</template>

<style scoped>
.notice-action {
  margin-left: auto;
  flex: none;
}
</style>
