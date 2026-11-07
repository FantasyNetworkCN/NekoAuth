<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AuthLayout from '@/components/AuthLayout.vue'
import AppIcon from '@/components/AppIcon.vue'
import { ApiError, api } from '@/lib/api'
import { auth } from '@/stores/auth'
import { showToast } from '@/lib/toast'
import type { RegistrationPolicy } from '@/types'

const route = useRoute()
const router = useRouter()
const form = reactive({
  nickname: '',
  email: '',
  captchaAnswer: '',
  verificationCode: '',
  password: '',
  confirmPassword: '',
})
const showPassword = ref(false)
const loading = ref(false)
const sendingCode = ref(false)
const codeCooldown = ref(0)
const error = ref('')
const fieldErrors = ref<Record<string, string>>({})
const captcha = reactive({
  id: '',
  image: '',
  expiresInSeconds: 300,
  loading: false,
})
const registrationPolicy = ref<RegistrationPolicy>({
  enabled: false,
  emailVerificationRequired: false,
  emailDomains: [],
  emailCodeExpiresInSeconds: 0,
  emailCodeResendAfterSeconds: 0,
  emailCodeDailyLimit: 0,
  emailCodeWindowSeconds: 0,
})
const policyLoaded = ref(false)
let cooldownTimer: ReturnType<typeof setInterval> | undefined

const redirectTarget = computed(() => {
  const value = route.query.redirect
  return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//') ? value : null
})
const loginLink = computed(() => ({
  name: 'login',
  query: redirectTarget.value ? { redirect: redirectTarget.value } : {},
}))
const nicknamePlaceholder = computed(() => {
  const localPart = form.email.split('@')[0]?.trim()
  return localPart ? `留空则使用「${[...localPart].slice(0, 20).join('')}」` : '可选，留空则使用邮箱前缀'
})
const passwordStrength = computed(() => {
  let score = 0
  if (form.password.length >= 6) score++
  if (/[A-Za-z]/.test(form.password) && /\d/.test(form.password)) score++
  if (form.password.length >= 10) score++
  if (/[^A-Za-z0-9]/.test(form.password)) score++
  const labels = ['软乎乎', '小猫爪', '渐入佳境', '星光守护', '传说级']
  return { score, label: labels[score] || '软乎乎' }
})

function emailValidationError() {
  if (!/^\S+@\S+\.\S+$/.test(form.email)) return '请输入有效的邮箱'
  if (form.email.trim().length > 100) return '邮箱不能超过 100 个字符'
  return ''
}

function validate() {
  const errors: Record<string, string> = {}
  const nickname = form.nickname.trim()
  if (nickname.length > 32) errors.nickname = '昵称不能超过 32 个字符'
  else if (/[\u0000-\u001f\u007f-\u009f]/.test(nickname)) errors.nickname = '昵称不能包含控制字符'
  const emailError = emailValidationError()
  if (emailError) errors.email = emailError
  else if (registrationPolicy.value.emailVerificationRequired && !/^\d{6}$/.test(form.verificationCode)) {
    errors.verificationCode = '请输入 6 位邮箱验证码'
  }
  if (form.password.length < 6 || form.password.length > 30) errors.password = '密码长度需为 6-30 个字符'
  if (form.confirmPassword !== form.password) errors.confirmPassword = '两次输入的密码不一致'
  fieldErrors.value = errors
  return Object.keys(errors).length === 0
}

function startCooldown(seconds: number) {
  if (cooldownTimer) clearInterval(cooldownTimer)
  codeCooldown.value = Math.max(1, Math.floor(seconds))
  cooldownTimer = setInterval(() => {
    codeCooldown.value = Math.max(0, codeCooldown.value - 1)
    if (codeCooldown.value === 0 && cooldownTimer) {
      clearInterval(cooldownTimer)
      cooldownTimer = undefined
    }
  }, 1000)
}

async function loadCaptcha() {
  if (captcha.loading) return
  captcha.loading = true
  form.captchaAnswer = ''
  try {
    const result = await api.registrationCaptcha()
    captcha.id = result.id
    captcha.image = result.image
    captcha.expiresInSeconds = result.expiresInSeconds
    fieldErrors.value = { ...fieldErrors.value, captcha: '' }
  } catch {
    captcha.id = ''
    captcha.image = ''
    fieldErrors.value = { ...fieldErrors.value, captcha: '人机验证加载失败，请点击刷新重试' }
  } finally {
    captcha.loading = false
  }
}

async function sendCode() {
  if (sendingCode.value || codeCooldown.value > 0 || !registrationPolicy.value.enabled) return
  const emailError = emailValidationError()
  const errors: Record<string, string> = {}
  if (emailError) errors.email = emailError
  if (!captcha.id) errors.captcha = '人机验证尚未加载，请刷新后重试'
  else if (!/^[A-Za-z0-9]{5}$/.test(form.captchaAnswer.trim())) {
    errors.captcha = '请输入图中的 5 位人机验证码'
  }
  fieldErrors.value = errors
  if (Object.keys(errors).length > 0) return

  error.value = ''
  sendingCode.value = true
  try {
    const result = await api.requestRegistrationCode(
      form.email.trim(),
      captcha.id,
      form.captchaAnswer.trim().toUpperCase(),
    )
    form.verificationCode = ''
    startCooldown(result.resendAfterSeconds)
    showToast(result.message, 'success')
    await loadCaptcha()
  } catch (caught) {
    if (caught instanceof ApiError) {
      error.value = caught.message
      fieldErrors.value = caught.fields
    } else {
      error.value = '验证码发送失败，请稍后重试'
    }
    await loadCaptcha()
  } finally {
    sendingCode.value = false
  }
}

async function submit() {
  if (loading.value || !validate()) return
  error.value = ''
  loading.value = true
  try {
    await auth.register({
      nickname: form.nickname.trim() || undefined,
      email: form.email.trim(),
      password: form.password,
      verificationCode: registrationPolicy.value.emailVerificationRequired ? form.verificationCode.trim() : '',
    })
    showToast('契约成立，欢迎来到梦幻网络喵！', 'success')
    if (redirectTarget.value) {
      await router.replace(redirectTarget.value)
    } else {
      await router.replace({ name: 'account' })
    }
  } catch (caught) {
    if (caught instanceof ApiError) {
      error.value = caught.message
      fieldErrors.value = caught.fields
    } else {
      error.value = '网络异常，请稍后重试'
    }
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    registrationPolicy.value = await api.registrationPolicy()
    policyLoaded.value = true
    if (registrationPolicy.value.emailVerificationRequired) void loadCaptcha()
  } catch {
    error.value = '注册策略加载失败，请刷新页面后重试'
  }
})

onUnmounted(() => {
  if (cooldownTimer) clearInterval(cooldownTimer)
})
</script>

<template>
  <AuthLayout
    eyebrow="New Contract · 猫娘契约"
    title="和梦幻网络签订契约"
    :subtitle="redirectTarget
      ? '先创建你的 Neko ID，小梦随后会带你回到发起授权的应用。'
      : '创建一张属于你的梦幻网络通行证。只需一分钟，就能加入整片幻想世界。'"
  >
    <div v-if="error" class="alert alert-error" role="alert">
      <AppIcon name="alert" :size="18" />
      <span>{{ error }}</span>
    </div>

    <div v-if="policyLoaded && !registrationPolicy.enabled" class="alert alert-error" role="alert">
      <AppIcon name="alert" :size="18" />
      <span>当前暂未开放新用户注册，请联系猫屋管理员。</span>
    </div>

    <form class="auth-form" novalidate @submit.prevent="submit">
      <label class="field">
        <span class="field-row">
          <span class="field-label">昵称</span>
          <span class="field-hint">1-32 个字符，不能包含违禁词，之后仍可修改</span>
        </span>
        <span class="input-shell" :class="{ 'has-error': fieldErrors.nickname }">
          <AppIcon name="user" :size="18" />
          <input
            v-model="form.nickname"
            name="nickname"
            type="text"
            autocomplete="nickname"
            :placeholder="nicknamePlaceholder"
            maxlength="32"
          />
        </span>
        <span v-if="fieldErrors.nickname" class="field-error">{{ fieldErrors.nickname }}</span>
      </label>

      <label class="field">
        <span class="field-label">邮箱地址</span>
        <span class="input-shell" :class="{ 'has-error': fieldErrors.email }">
          <AppIcon name="mail" :size="18" />
          <input v-model="form.email" name="email" type="email" autocomplete="email" placeholder="name@example.com" maxlength="100" required />
        </span>
        <span v-if="fieldErrors.email" class="field-error">{{ fieldErrors.email }}</span>
      </label>

      <div v-if="registrationPolicy.emailVerificationRequired" class="field captcha-field">
        <span class="field-label">人机验证</span>
        <div class="captcha-row">
          <button
            class="captcha-visual"
            type="button"
            :disabled="captcha.loading"
            aria-label="刷新人机验证码"
            @click="loadCaptcha"
          >
            <img v-if="captcha.image" :src="captcha.image" alt="人机验证码图片，点击刷新" />
            <span v-else>{{ captcha.loading ? '加载中…' : '点击加载' }}</span>
          </button>
          <span class="input-shell captcha-input" :class="{ 'has-error': fieldErrors.captcha }">
            <AppIcon name="shield-cat" :size="18" />
            <input
              v-model="form.captchaAnswer"
              name="captchaAnswer"
              type="text"
              inputmode="text"
              autocomplete="off"
              autocapitalize="characters"
              spellcheck="false"
              placeholder="5 位字符"
              maxlength="5"
              pattern="[A-Za-z0-9]{5}"
              required
            />
          </span>
          <button class="captcha-refresh" type="button" :disabled="captcha.loading" aria-label="换一张人机验证码" @click="loadCaptcha">
            <AppIcon name="refresh" :size="16" />
          </button>
        </div>
        <span class="field-hint">验证码一次性使用；点击图片或刷新按钮可换一张。</span>
        <span v-if="fieldErrors.captcha" class="field-error">{{ fieldErrors.captcha }}</span>
      </div>

      <label v-if="registrationPolicy.emailVerificationRequired" class="field">
        <span class="field-label">邮箱验证码</span>
        <span class="input-shell" :class="{ 'has-error': fieldErrors.verificationCode }">
          <AppIcon name="shield-cat" :size="18" />
          <input
            v-model="form.verificationCode"
            name="verificationCode"
            type="text"
            inputmode="numeric"
            autocomplete="one-time-code"
            placeholder="6 位数字验证码"
            maxlength="6"
            pattern="\d{6}"
            required
          />
          <button
            class="input-action code-action"
            type="button"
            :disabled="sendingCode || codeCooldown > 0 || captcha.loading || !captcha.id || !registrationPolicy.enabled"
            @click="sendCode"
          >
            {{ sendingCode ? '发送中…' : codeCooldown > 0 ? `${codeCooldown}s 后重发` : '获取验证码' }}
          </button>
        </span>
        <span v-if="fieldErrors.verificationCode" class="field-error">{{ fieldErrors.verificationCode }}</span>
      </label>

      <label class="field">
        <span class="field-row">
          <span class="field-label">秘密咒语（密码）</span>
          <span class="strength-label" :data-score="passwordStrength.score">{{ passwordStrength.score ? passwordStrength.label : '6-30 位' }}</span>
        </span>
        <span class="input-shell" :class="{ 'has-error': fieldErrors.password }">
          <AppIcon name="lock" :size="18" />
          <input
            v-model="form.password"
            name="password"
            :type="showPassword ? 'text' : 'password'"
            autocomplete="new-password"
            placeholder="6-30 个字符"
            maxlength="30"
            required
          />
          <button class="input-action" type="button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword">
            <AppIcon :name="showPassword ? 'eye-off' : 'eye'" :size="18" />
          </button>
        </span>
        <span class="password-meter" :data-score="passwordStrength.score" aria-hidden="true"><i></i><i></i><i></i><i></i></span>
        <span v-if="fieldErrors.password" class="field-error">{{ fieldErrors.password }}</span>
      </label>

      <label class="field">
        <span class="field-label">再念一遍咒语</span>
        <span class="input-shell" :class="{ 'has-error': fieldErrors.confirmPassword }">
          <AppIcon name="shield-cat" :size="18" />
          <input
            v-model="form.confirmPassword"
            name="confirmPassword"
            :type="showPassword ? 'text' : 'password'"
            autocomplete="new-password"
            placeholder="确认密码"
            maxlength="30"
            required
          />
        </span>
        <span v-if="fieldErrors.confirmPassword" class="field-error">{{ fieldErrors.confirmPassword }}</span>
      </label>

      <div class="contract-note">
        <span class="contract-paw">🐾</span>
        <span>签订契约即表示你同意梦幻网络的服务条款与隐私约定。小梦不会偷看你的秘密咒语。</span>
      </div>

      <button class="btn btn-primary btn-block" type="submit" :disabled="loading || !policyLoaded || !registrationPolicy.enabled">
        <span v-if="loading" class="spinner"></span>
        <template v-else>
          <AppIcon name="heart" :size="17" />
          缔结契约
          <AppIcon name="arrow-right" :size="18" />
        </template>
      </button>
    </form>

    <div class="auth-switch">
      已经有通行证了？
      <RouterLink :to="loginLink">回到猫娘身边</RouterLink>
    </div>
  </AuthLayout>
</template>

<style scoped>
.strength-label {
  color: var(--muted);
  font-size: 0.56rem;
}

.registration-guide {
  display: flex;
  align-items: flex-start;
  gap: 0.65rem;
  padding: 0.75rem 0.8rem;
  border: 1px solid var(--line);
  border-radius: 12px;
  color: var(--body);
  background: rgba(255, 157, 122, 0.045);
  font-size: 0.61rem;
  line-height: 1.6;
}

.registration-guide-icon {
  display: grid;
  width: 34px;
  height: 34px;
  flex: none;
  border: 1px solid rgba(255, 157, 122, 0.16);
  border-radius: 10px;
  color: var(--accent);
  background: rgba(255, 157, 122, 0.08);
  place-items: center;
}

.registration-guide strong {
  display: block;
  margin-bottom: 0.12rem;
  color: var(--ink);
  font-size: 0.65rem;
  font-weight: 700;
}

.registration-guide p { margin: 0; }

.strength-label[data-score='1'] { color: #ff9aa0; }
.strength-label[data-score='2'] { color: var(--gold); }
.strength-label[data-score='3'] { color: #ffc29c; }
.strength-label[data-score='4'] { color: var(--success); }

.code-action {
  width: auto;
  padding: 0 0.4rem;
  font-size: 0.62rem;
  font-weight: 600;
  white-space: nowrap;
}

.code-action:disabled {
  cursor: default;
  opacity: 0.48;
}

.captcha-row {
  display: grid;
  grid-template-columns: 124px minmax(0, 1fr) 34px;
  gap: 0.45rem;
  align-items: center;
}

.captcha-visual {
  display: grid;
  height: 42px;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 10px;
  color: var(--muted);
  background: rgba(13, 10, 8, 0.72);
  font-size: 0.58rem;
  place-items: center;
  cursor: pointer;
}

.captcha-visual img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.captcha-visual:disabled,
.captcha-refresh:disabled {
  cursor: default;
  opacity: 0.55;
}

.captcha-input {
  min-width: 0;
}

.captcha-input input {
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.captcha-refresh {
  display: grid;
  width: 34px;
  height: 34px;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: 10px;
  color: var(--accent);
  background: rgba(255, 255, 255, 0.035);
  place-items: center;
  cursor: pointer;
}

.password-meter {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.28rem;
  margin-top: -0.02rem;
}

.password-meter i {
  height: 3px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.07);
  transition: background 0.2s ease;
}

.password-meter[data-score='1'] i:nth-child(-n+1) { background: #ff9aa0; }
.password-meter[data-score='2'] i:nth-child(-n+2) { background: var(--gold); }
.password-meter[data-score='3'] i:nth-child(-n+3) { background: #ffc29c; }
.password-meter[data-score='4'] i { background: var(--success); }

.contract-note {
  display: flex;
  align-items: flex-start;
  gap: 0.55rem;
  padding: 0.65rem 0.72rem;
  border: 1px solid var(--line);
  border-radius: 11px;
  color: var(--muted);
  background: rgba(255, 157, 122, 0.035);
  font-size: 0.58rem;
  line-height: 1.6;
}

.contract-paw {
  flex: none;
  filter: grayscale(0.35);
}
</style>
