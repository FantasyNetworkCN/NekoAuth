<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import AuthLayout from '@/components/AuthLayout.vue'
import AppIcon from '@/components/AppIcon.vue'
import { ApiError, api } from '@/lib/api'
import { showToast } from '@/lib/toast'

const router = useRouter()
const form = reactive({
  email: '',
  captchaAnswer: '',
  code: '',
  newPassword: '',
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
let cooldownTimer: ReturnType<typeof setInterval> | undefined

const passwordStrength = computed(() => {
  let score = 0
  if (form.newPassword.length >= 6) score++
  if (/[A-Za-z]/.test(form.newPassword) && /\d/.test(form.newPassword)) score++
  if (form.newPassword.length >= 10) score++
  if (/[^A-Za-z0-9]/.test(form.newPassword)) score++
  const labels = ['软乎乎', '小猫爪', '渐入佳境', '星光守护', '传说级']
  return { score, label: labels[score] || '软乎乎' }
})

function emailValidationError() {
  if (!/^\S+@\S+\.\S+$/.test(form.email)) return '请输入有效的邮箱'
  if (form.email.trim().length > 100) return '邮箱不能超过 100 个字符'
  return ''
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
  if (sendingCode.value || codeCooldown.value > 0) return
  const errors: Record<string, string> = {}
  const emailError = emailValidationError()
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
    const result = await api.requestPasswordResetCode(
      form.email.trim(),
      captcha.id,
      form.captchaAnswer.trim().toUpperCase(),
    )
    form.code = ''
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
  if (loading.value) return
  const errors: Record<string, string> = {}
  const emailError = emailValidationError()
  if (emailError) errors.email = emailError
  if (!/^\d{6}$/.test(form.code.trim())) errors.code = '请输入 6 位邮箱验证码'
  if (form.newPassword.length < 6 || form.newPassword.length > 30) {
    errors.newPassword = '密码长度需为 6-30 个字符'
  }
  if (form.confirmPassword !== form.newPassword) errors.confirmPassword = '两次输入的密码不一致'
  fieldErrors.value = errors
  if (Object.keys(errors).length > 0) return

  loading.value = true
  error.value = ''
  try {
    await api.resetPassword(form.email.trim(), form.code.trim(), form.newPassword)
    showToast('密码已重置，请使用新密码登录', 'success')
    await router.replace({ name: 'login', query: { reason: 'password-reset' } })
  } catch (caught) {
    if (caught instanceof ApiError) {
      error.value = caught.message
      fieldErrors.value = caught.fields
    } else {
      error.value = '密码重置失败，请稍后重试'
    }
  } finally {
    loading.value = false
  }
}

onMounted(loadCaptcha)
onUnmounted(() => {
  if (cooldownTimer) clearInterval(cooldownTimer)
})
</script>

<template>
  <AuthLayout
    eyebrow="Neko Identity Recovery"
    title="把秘密咒语找回来"
    subtitle="先完成人机验证并接收邮箱验证码，再设置新的通行证密码。重置成功后，所有旧会话都会失效。"
  >
    <div v-if="error" class="alert alert-error" role="alert">
      <AppIcon name="alert" :size="18" />
      <span>{{ error }}</span>
    </div>

    <form class="auth-form" novalidate @submit.prevent="submit">
      <label class="field">
        <span class="field-label">注册邮箱</span>
        <span class="input-shell" :class="{ 'has-error': fieldErrors.email }">
          <AppIcon name="mail" :size="18" />
          <input
            v-model="form.email"
            name="email"
            type="email"
            autocomplete="email"
            placeholder="neko@example.com"
            maxlength="100"
            required
          />
        </span>
        <span v-if="fieldErrors.email" class="field-error">{{ fieldErrors.email }}</span>
      </label>

      <div class="field captcha-field">
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
        <span v-if="fieldErrors.captcha" class="field-error">{{ fieldErrors.captcha }}</span>
      </div>

      <label class="field">
        <span class="field-label">邮箱验证码</span>
        <span class="input-shell" :class="{ 'has-error': fieldErrors.code }">
          <AppIcon name="shield-cat" :size="18" />
          <input
            v-model="form.code"
            name="code"
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
            :disabled="sendingCode || codeCooldown > 0 || captcha.loading || !captcha.id"
            @click="sendCode"
          >
            {{ sendingCode ? '发送中…' : codeCooldown > 0 ? `${codeCooldown}s 后重发` : '获取验证码' }}
          </button>
        </span>
        <span v-if="fieldErrors.code" class="field-error">{{ fieldErrors.code }}</span>
      </label>

      <label class="field">
        <span class="field-row">
          <span class="field-label">新的秘密咒语</span>
          <span class="strength-label" :data-score="passwordStrength.score">{{ passwordStrength.score ? passwordStrength.label : '6-30 位' }}</span>
        </span>
        <span class="input-shell" :class="{ 'has-error': fieldErrors.newPassword }">
          <AppIcon name="lock" :size="18" />
          <input
            v-model="form.newPassword"
            name="newPassword"
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
        <span v-if="fieldErrors.newPassword" class="field-error">{{ fieldErrors.newPassword }}</span>
      </label>

      <label class="field">
        <span class="field-label">再念一遍新咒语</span>
        <span class="input-shell" :class="{ 'has-error': fieldErrors.confirmPassword }">
          <AppIcon name="shield-cat" :size="18" />
          <input
            v-model="form.confirmPassword"
            name="confirmPassword"
            :type="showPassword ? 'text' : 'password'"
            autocomplete="new-password"
            placeholder="确认新密码"
            maxlength="30"
            required
          />
        </span>
        <span v-if="fieldErrors.confirmPassword" class="field-error">{{ fieldErrors.confirmPassword }}</span>
      </label>

      <button class="btn btn-primary btn-block" type="submit" :disabled="loading">
        <span v-if="loading" class="spinner"></span>
        <template v-else>
          <AppIcon name="key" :size="17" />
          重置密码
          <AppIcon name="arrow-right" :size="18" />
        </template>
      </button>
    </form>

    <div class="auth-switch">
      想起来了？
      <RouterLink to="/login">返回登录</RouterLink>
    </div>
  </AuthLayout>
</template>

<style scoped>
.form-links {
  display: inline-flex;
  gap: 12px;
  align-items: center;
}

.captcha-field {
  gap: 8px;
}

.captcha-row {
  display: grid;
  grid-template-columns: 134px minmax(0, 1fr) 40px;
  gap: 10px;
  align-items: stretch;
}

.captcha-visual,
.captcha-refresh {
  border: 1px solid var(--line, rgba(255, 255, 255, 0.14));
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.06);
  color: var(--text-muted, #a6a8c6);
  cursor: pointer;
}

.captcha-visual {
  display: grid;
  place-items: center;
  height: 48px;
  padding: 0;
  overflow: hidden;
}

.captcha-visual img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.captcha-input {
  min-width: 0;
}

.captcha-refresh {
  display: grid;
  place-items: center;
}

.captcha-visual:disabled,
.captcha-refresh:disabled {
  cursor: wait;
  opacity: 0.6;
}

@media (max-width: 560px) {
  .captcha-row {
    grid-template-columns: 112px minmax(0, 1fr) 40px;
  }
}
</style>
