<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AuthLayout from '@/components/AuthLayout.vue'
import AppIcon from '@/components/AppIcon.vue'
import { ApiError, api } from '@/lib/api'
import { auth } from '@/stores/auth'
import { showToast } from '@/lib/toast'
import type { LoginStepUp } from '@/types'

const route = useRoute()
const router = useRouter()
const loginHint = route.query.login_hint
const identifier = ref(typeof loginHint === 'string' ? loginHint : '')
const password = ref('')
const showPassword = ref(false)
const loading = ref(false)
const loggingOut = ref(false)
const error = ref('')
const stepUp = ref<LoginStepUp | null>(null)
const verificationCode = ref('')
const verifying = ref(false)
const resending = ref(false)
const resendIn = ref(0)
let resendTimer: ReturnType<typeof setInterval> | undefined

const redirectTarget = computed(() => {
  const value = route.query.redirect
  return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//') ? value : null
})
const externalReturnTo = computed(() => {
  const value = route.query.return_to
  return typeof value === 'string' && /^https?:\/\//i.test(value) ? value : null
})
const signedInWithoutRedirect = computed(() => !redirectTarget.value && auth.isAuthenticated.value)
const isOAuthFlow = computed(() => redirectTarget.value?.startsWith('/authorize') === true)
const registerLink = computed(() => ({
  name: 'register',
  query: redirectTarget.value ? { redirect: redirectTarget.value } : {},
}))
const stepUpMethodLabel = computed(() => stepUp.value?.method === 'sms' ? '短信' : '邮件')
const alternativeMethod = computed(() => {
  if (!stepUp.value) return null
  return stepUp.value.methods.find((method) => method !== stepUp.value?.method) || null
})

const notice = computed(() => {
  if (route.query.reason === 'expired') return '猫娘小梦打了个盹，登录状态过期了。请重新登录喵。'
  if (route.query.reason === 'forbidden') return '这间管理员猫屋需要 ADMIN 身份，请换一个管理员账户。'
  if (route.query.reason === 'registered') return '账户已经织入梦幻网络，可以登录啦。'
  if (route.query.reason === 'password-changed') return '密码已更新，所有旧会话都被猫爪拍散了，请重新登录。'
  if (route.query.reason === 'password-reset') return '密码已重置，所有旧会话都已失效，请使用新密码登录。'
  return ''
})

function startResendCooldown(seconds: number) {
  if (resendTimer) clearInterval(resendTimer)
  resendIn.value = Math.max(0, seconds)
  resendTimer = setInterval(() => {
    resendIn.value = Math.max(0, resendIn.value - 1)
    if (resendIn.value === 0 && resendTimer) {
      clearInterval(resendTimer)
      resendTimer = undefined
    }
  }, 1000)
}

async function finishLogin() {
  const name = auth.state.user?.displayName || auth.state.user?.username
  if (externalReturnTo.value) {
    const resolved = await api.resolveWebEntry(externalReturnTo.value)
    showToast(`欢迎回来，${name}`, 'success')
    window.location.assign(resolved.returnTo)
    return
  }
  if (redirectTarget.value) {
    showToast(`欢迎回来，${name}`, 'success')
    await router.replace(redirectTarget.value)
    return
  }
  showToast(`欢迎回来，${name}`, 'success')
  await router.replace({ name: 'account' })
}

async function submit() {
  if (loading.value) return
  error.value = ''
  loading.value = true
  try {
    const challenge = await auth.login(identifier.value.trim(), password.value)
    if (challenge) {
      stepUp.value = challenge
      password.value = ''
      verificationCode.value = ''
      startResendCooldown(challenge.resendAfterSeconds)
      return
    }
    await finishLogin()
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : '网络异常，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function verifyStepUp() {
  if (!stepUp.value || verifying.value || verificationCode.value.length !== 6) return
  error.value = ''
  verifying.value = true
  try {
    await auth.verifyLoginStepUp(stepUp.value.challengeId, verificationCode.value)
    await finishLogin()
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : '验证失败，请稍后重试'
  } finally {
    verifying.value = false
  }
}

async function resendStepUp(method?: 'email' | 'sms') {
  if (!stepUp.value || resending.value) return
  error.value = ''
  resending.value = true
  try {
    stepUp.value = await auth.resendLoginStepUp(stepUp.value.challengeId, method)
    verificationCode.value = ''
    startResendCooldown(stepUp.value.resendAfterSeconds)
    showToast(`验证码已通过${stepUpMethodLabel.value}重新发送`, 'success')
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : '验证码发送失败，请稍后重试'
  } finally {
    resending.value = false
  }
}

function resetStepUp() {
  stepUp.value = null
  verificationCode.value = ''
  error.value = ''
  if (resendTimer) clearInterval(resendTimer)
  resendTimer = undefined
  resendIn.value = 0
}

onMounted(async () => {
  if (!externalReturnTo.value || !auth.isAuthenticated.value) return
  try {
    const resolved = await api.resolveWebEntry(externalReturnTo.value)
    window.location.replace(resolved.returnTo)
  } catch {
    error.value = '登录返回地址无效，请重新进入应用。'
  }
})

onBeforeUnmount(() => {
  if (resendTimer) clearInterval(resendTimer)
})

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
      : '使用梦幻网络通行证登录，管理密码与登录设备，或继续访问已接入的 OAuth 应用。'"
  >
    <div v-if="signedInWithoutRedirect" class="notice-banner">
      <AppIcon name="check" :size="17" />
      <span>当前已登录为 {{ auth.state.user?.displayName || auth.state.user?.username }}。</span>
      <RouterLink class="btn btn-secondary btn-small notice-action" :to="{ name: 'account' }">账户管理</RouterLink>
      <button class="btn btn-ghost btn-small notice-action" type="button" :disabled="loggingOut" @click="logout">
        退出账户
      </button>
    </div>

    <div v-if="notice" class="notice-banner">
      <AppIcon name="alert" :size="17" />
      <span>{{ notice }}</span>
    </div>

    <div v-if="error" class="alert alert-error" role="alert">
      <AppIcon name="alert" :size="18" />
      <span>{{ error }}</span>
    </div>

    <form v-if="!stepUp" class="auth-form" novalidate @submit.prevent="submit">
      <label class="field">
        <span class="field-label">邮箱、手机号或用户名</span>
        <span class="input-shell" :class="{ 'has-error': error }">
          <AppIcon name="user" :size="18" />
          <input
            v-model="identifier"
            name="identifier"
            type="text"
            autocomplete="username"
            placeholder="输入邮箱、手机号或用户名"
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
        <span class="form-links">
          <RouterLink class="text-link" to="/forgot-password">忘记密码？</RouterLink>
          <RouterLink class="text-link" :to="registerLink">第一次来？</RouterLink>
        </span>
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

    <form v-else class="auth-form step-up-form" novalidate @submit.prevent="verifyStepUp">
      <div class="step-up-heading">
        <span class="step-up-icon"><AppIcon name="shield-cat" :size="22" /></span>
        <div>
          <h2>还需要一次确认</h2>
          <p>当前登录环境或网络与此前不同。验证码已通过{{ stepUpMethodLabel }}发送至
            <strong>{{ stepUp.maskedTarget }}</strong>。</p>
        </div>
      </div>

      <label class="field">
        <span class="field-label">{{ stepUpMethodLabel }}验证码</span>
        <span class="input-shell" :class="{ 'has-error': error }">
          <AppIcon name="key" :size="18" />
          <input
            v-model="verificationCode"
            name="verificationCode"
            type="text"
            inputmode="numeric"
            pattern="[0-9]*"
            autocomplete="one-time-code"
            placeholder="输入 6 位验证码"
            maxlength="6"
            required
            autofocus
          />
        </span>
      </label>

      <p class="step-up-expiry">验证窗口约 {{ Math.ceil(stepUp.expiresInSeconds / 60) }} 分钟，请勿转发验证码。</p>

      <button
        class="btn btn-primary btn-block"
        type="submit"
        :disabled="verifying || verificationCode.length !== 6"
      >
        <span v-if="verifying" class="spinner"></span>
        <template v-else>
          <AppIcon name="shield" :size="17" />
          完成验证并登录
          <AppIcon name="arrow-right" :size="18" />
        </template>
      </button>

      <div class="step-up-actions">
        <button
          class="btn btn-ghost btn-small"
          type="button"
          :disabled="resending || resendIn > 0"
          @click="resendStepUp()"
        >
          {{ resendIn > 0 ? `${resendIn} 秒后可重发` : '重新发送验证码' }}
        </button>
        <button
          v-if="alternativeMethod"
          class="btn btn-ghost btn-small"
          type="button"
          :disabled="resending"
          @click="resendStepUp(alternativeMethod)"
        >
          改用{{ alternativeMethod === 'sms' ? '短信' : '邮件' }}
        </button>
        <button class="btn btn-ghost btn-small" type="button" :disabled="resending" @click="resetStepUp">
          返回重新登录
        </button>
      </div>
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

.form-links {
  display: inline-flex;
  gap: 12px;
  align-items: center;
}

.step-up-heading {
  display: flex;
  gap: 0.8rem;
  align-items: flex-start;
  padding: 0.9rem;
  border: 1px solid color-mix(in srgb, var(--accent) 34%, transparent);
  border-radius: var(--r-md);
  background: color-mix(in srgb, var(--accent) 8%, transparent);
}

.step-up-icon {
  display: inline-grid;
  width: 38px;
  height: 38px;
  flex: none;
  place-items: center;
  border-radius: 50%;
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 13%, transparent);
}

.step-up-heading h2 {
  margin: 0;
  color: var(--ink);
  font-size: 0.86rem;
  letter-spacing: -0.02em;
}

.step-up-heading p {
  margin: 0.28rem 0 0;
  color: var(--body);
  font-size: 0.66rem;
  line-height: 1.65;
}

.step-up-heading strong {
  color: var(--ink);
  font-family: var(--font-mono);
  font-weight: 650;
}

.step-up-expiry {
  margin-top: -0.15rem;
  color: var(--muted);
  font-size: 0.56rem;
}

.step-up-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  justify-content: center;
  margin-top: 0.25rem;
}

</style>
