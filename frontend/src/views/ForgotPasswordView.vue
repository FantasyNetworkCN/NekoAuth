<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import AuthLayout from '@/components/AuthLayout.vue'
import AppIcon from '@/components/AppIcon.vue'
import { ApiError, api, type ContactKind } from '@/lib/api'
import { showToast } from '@/lib/toast'

const router = useRouter()
const method = ref<ContactKind>('email')
const smsEnabled = ref(false)
const isPhone = computed(() => method.value === 'phone')
const contactLabel = computed(() => isPhone.value ? '手机号' : '邮箱')
const codeLabel = computed(() => isPhone.value ? '短信验证码' : '邮箱验证码')
const form = reactive({
  email: '',
  phone: '',
  captchaAnswer: '',
  code: '',
  newPassword: '',
  confirmPassword: '',
})
const showPassword = ref(false)
const loading = ref(false)
const sendingCode = ref(false)
const resendAt = reactive({ email: 0, phone: 0 })
const clock = ref(Date.now())
const codeCooldown = computed(() => Math.max(0, Math.ceil((resendAt[method.value] - clock.value) / 1000)))
const error = ref('')
const sendNotice = ref('')
const fieldErrors = ref<Record<string, string>>({})
const captcha = reactive({
  id: '',
  image: '',
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

function contactValidationError() {
  if (isPhone.value) return /^(?:\+86)?1[3-9][0-9]{9}$/.test(form.phone) ? '' : '请输入有效的中国大陆手机号'
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) return '请输入有效的邮箱'
  if (form.email.length > 100) return '邮箱不能超过 100 个字符'
  return ''
}

function setMethod(next: ContactKind) {
  if (method.value === next || loading.value || sendingCode.value) return
  method.value = next
  form.code = ''
  sendNotice.value = ''
  fieldErrors.value = {}
  error.value = ''
}

function startCooldown(channel: ContactKind, seconds: number) {
  clock.value = Date.now()
  resendAt[channel] = clock.value + seconds * 1000
  if (cooldownTimer) return
  cooldownTimer = setInterval(() => {
    clock.value = Date.now()
    if (clock.value >= Math.max(resendAt.email, resendAt.phone)) {
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
  if (loading.value || sendingCode.value || captcha.loading || codeCooldown.value > 0) return
  const channel = method.value
  sendNotice.value = ''
  const errors: Record<string, string> = {}
  const contactError = contactValidationError()
  if (contactError) errors[channel] = contactError
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
      channel,
      form[channel],
      captcha.id,
      form.captchaAnswer.trim().toUpperCase(),
    )
    form.code = ''
    startCooldown(channel, result.resendAfterSeconds)
    sendNotice.value = result.message
  } catch (caught) {
    if (caught instanceof ApiError) {
      error.value = caught.message
      fieldErrors.value = { ...caught.fields, captcha: caught.fields.captchaAnswer || caught.fields.captchaId || '' }
    } else {
      error.value = '验证码发送失败，请稍后重试'
    }
  } finally {
    await loadCaptcha()
    sendingCode.value = false
  }
}

async function submit() {
  if (loading.value || sendingCode.value) return
  const errors: Record<string, string> = {}
  const contactError = contactValidationError()
  if (contactError) errors[method.value] = contactError
  if (!/^\d{6}$/.test(form.code.trim())) errors.code = `请输入 6 位${codeLabel.value}`
  if (form.newPassword.length < 6 || form.newPassword.length > 30) {
    errors.newPassword = '密码长度需为 6-30 个字符'
  }
  if (form.confirmPassword !== form.newPassword) errors.confirmPassword = '两次输入的密码不一致'
  fieldErrors.value = errors
  if (Object.keys(errors).length > 0) return

  loading.value = true
  error.value = ''
  try {
    await api.resetPassword(method.value, form[method.value], form.code.trim(), form.newPassword)
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

onMounted(() => {
  void loadCaptcha()
  void api.passwordResetOptions().then(options => {
    smsEnabled.value = options.smsEnabled
  }).catch(() => {})
})
onUnmounted(() => {
  if (cooldownTimer) clearInterval(cooldownTimer)
})
</script>

<template>
  <AuthLayout
    eyebrow="Neko Identity Recovery"
    title="把秘密咒语找回来"
    subtitle="验证已绑定的联系方式后，即可重置密码。重置后，所有旧会话都会失效。"
  >
    <div v-if="error" class="alert alert-error" role="alert">
      <AppIcon name="alert" :size="18" />
      <span>{{ error }}</span>
    </div>

    <form class="auth-form recovery-form" novalidate @submit.prevent="submit">
      <div v-if="smsEnabled" class="recovery-methods" role="group" aria-label="找回方式">
        <button type="button" :aria-pressed="!isPhone" :disabled="loading || sendingCode" @click="setMethod('email')">
          <AppIcon name="mail" :size="16" /> 邮箱找回
        </button>
        <button type="button" :aria-pressed="isPhone" :disabled="loading || sendingCode" @click="setMethod('phone')">
          <AppIcon name="smartphone" :size="16" /> 手机号找回
        </button>
      </div>

      <label class="field">
        <span class="field-label">已绑定{{ contactLabel }}</span>
        <span class="input-shell" :class="{ 'has-error': fieldErrors[method] }">
          <AppIcon :name="isPhone ? 'smartphone' : 'mail'" :size="18" />
          <input
            :key="method"
            v-model.trim="form[method]"
            :name="method"
            :type="isPhone ? 'tel' : 'email'"
            :autocomplete="isPhone ? 'tel' : 'email'"
            :placeholder="isPhone ? '中国大陆手机号' : 'neko@example.com'"
            :maxlength="isPhone ? 14 : 100"
            :disabled="loading || sendingCode"
            @input="form.code = ''; sendNotice = ''"
            required
          />
        </span>
        <span v-if="fieldErrors[method]" class="field-error">{{ fieldErrors[method] }}</span>
      </label>

      <div class="field captcha-field">
        <label for="recovery-captcha" class="field-label">人机验证</label>
        <div class="captcha-row">
          <span class="input-shell captcha-input" :class="{ 'has-error': fieldErrors.captcha }">
            <input
              id="recovery-captcha"
              v-model="form.captchaAnswer"
              name="captchaAnswer"
              type="text"
              autocomplete="off"
              autocapitalize="characters"
              spellcheck="false"
              placeholder="5 位字符"
              :disabled="sendingCode"
              maxlength="5"
              pattern="[A-Za-z0-9]{5}"
              required
            />
          </span>
          <button
            class="captcha-visual"
            type="button"
            :disabled="captcha.loading || sendingCode"
            aria-label="刷新人机验证码"
            title="刷新人机验证码"
            @click="loadCaptcha"
          >
            <img v-if="captcha.image" :src="captcha.image" alt="" />
            <span v-else>{{ captcha.loading ? '加载中…' : '点击加载' }}</span>
          </button>
        </div>
        <span v-if="fieldErrors.captcha" class="field-error">{{ fieldErrors.captcha }}</span>
      </div>

      <div class="field">
        <label class="field-label" for="recovery-code">{{ codeLabel }}</label>
        <div class="code-row">
          <span class="input-shell" :class="{ 'has-error': fieldErrors.code }">
            <input
              id="recovery-code"
              v-model="form.code"
              name="code"
              type="text"
              inputmode="numeric"
              autocomplete="one-time-code"
              placeholder="6 位数字"
              maxlength="6"
              pattern="[0-9]{6}"
              required
            />
          </span>
          <button
            class="btn btn-secondary code-action"
            type="button"
            :disabled="loading || sendingCode || codeCooldown > 0 || captcha.loading || !captcha.id"
            @click="sendCode"
          >
            {{ sendingCode ? '发送中…' : codeCooldown > 0 ? `${codeCooldown} 秒后重发` : '获取验证码' }}
          </button>
        </div>
        <span v-if="fieldErrors.code" class="field-error">{{ fieldErrors.code }}</span>
        <span v-if="sendNotice" class="send-notice" role="status">{{ sendNotice }}</span>
      </div>

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
          <button class="input-action" type="button" :aria-pressed="showPassword" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword">
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

      <button class="btn btn-primary btn-block" type="submit" :disabled="loading || sendingCode">
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
.recovery-form,
.recovery-form .field,
.recovery-form .input-shell {
  min-width: 0;
}

.recovery-methods {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px;
  padding: 4px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--bg-soft);
}

.recovery-methods button {
  display: flex;
  min-height: 44px;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  padding: 0 0.25rem;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--body);
  font: inherit;
  font-size: 0.68rem;
  cursor: pointer;
}

.recovery-methods button[aria-pressed='true'] {
  border-color: var(--line);
  background: var(--surface-raised);
  color: var(--ink);
}

.recovery-methods button:hover:not(:disabled) {
  color: var(--ink);
}

.captcha-row,
.code-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(100px, 38%);
  gap: 0.5rem;
  align-items: stretch;
}

.captcha-visual {
  display: grid;
  min-width: 0;
  min-height: 48px;
  place-items: center;
  overflow: hidden;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--bg-soft);
  color: var(--muted);
  font-size: 0.65rem;
  cursor: pointer;
}

.captcha-visual img {
  display: block;
  width: 100%;
  height: 46px;
  object-fit: contain;
}

.captcha-input input {
  text-transform: uppercase;
}

.code-row {
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 108px), 1fr));
}

.code-action.btn {
  min-height: 48px;
  padding-inline: 0.35rem;
  font-size: 0.68rem;
}

.send-notice {
  color: var(--body);
  font-size: 0.68rem;
}

.captcha-visual:hover:not(:disabled) {
  border-color: var(--accent);
}

.captcha-visual:disabled,
.recovery-methods button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.recovery-form button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
</style>
