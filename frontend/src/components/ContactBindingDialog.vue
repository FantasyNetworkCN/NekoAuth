<script setup lang="ts">
import { computed, nextTick, onUnmounted, reactive, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { ApiError, api, type ContactKind } from '@/lib/api'
import { showToast } from '@/lib/toast'
import { auth } from '@/stores/auth'
import type { RegistrationCaptcha } from '@/types'

const dialog = ref<HTMLDialogElement>()
const codeInput = ref<HTMLInputElement>()
const emit = defineEmits<{ bound: [kind: ContactKind] }>()
const kind = ref<ContactKind>('email')
const label = computed(() => kind.value === 'email' ? '邮箱' : '手机号')
const form = reactive({ contact: '', captchaAnswer: '', verificationCode: '', currentPassword: '' })
const captcha = ref<RegistrationCaptcha>()
const captchaLoading = ref(false)
const captchaError = ref('')
const sending = ref(false)
const binding = ref(false)
const sentTo = ref('')
const cooldown = ref(0)
const feedback = ref('')
const error = ref('')
let timer: ReturnType<typeof setInterval> | undefined
let captchaRequest = 0

const target = computed(() => {
  const input = form.contact.trim()
  return kind.value === 'email' ? input.toLowerCase() : input.replace(/^\+86/, '')
})
const sent = computed(() => sentTo.value !== '' && sentTo.value === target.value)

function stopCountdown() {
  clearInterval(timer)
  timer = undefined
  cooldown.value = 0
}

function reset() {
  captchaRequest++
  captcha.value = undefined
  captchaLoading.value = false
  captchaError.value = ''
  Object.assign(form, { contact: '', captchaAnswer: '', verificationCode: '', currentPassword: '' })
  sentTo.value = ''
  error.value = ''
  feedback.value = ''
  stopCountdown()
}

function open(channel: ContactKind) {
  if (!dialog.value || dialog.value.open || sending.value || binding.value) return
  reset()
  kind.value = channel
  dialog.value.showModal()
  void loadCaptcha()
}

defineExpose({ open })
onUnmounted(reset)

async function loadCaptcha() {
  const request = ++captchaRequest
  captchaLoading.value = true
  captcha.value = undefined
  captchaError.value = ''
  form.captchaAnswer = ''
  try {
    const result = await api.registrationCaptcha()
    if (request === captchaRequest) captcha.value = result
  } catch {
    if (request === captchaRequest) captchaError.value = '图形验证码加载失败，请点击刷新重试。'
  } finally {
    if (request === captchaRequest) captchaLoading.value = false
  }
}

function contactChanged() {
  error.value = ''
  feedback.value = ''
  form.verificationCode = ''
  form.currentPassword = ''
}

function messageFor(caught: unknown, fallback: string) {
  if (!(caught instanceof ApiError)) return fallback
  return Object.values(caught.fields)[0] || caught.message
}

async function sendCode() {
  if (sending.value || binding.value || captchaLoading.value || cooldown.value > 0) return
  error.value = ''
  const contact = target.value
  const valid = kind.value === 'email'
    ? /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(contact) && contact.length <= 100
    : /^(?:\+86)?1[3-9][0-9]{9}$/.test(form.contact.trim())
  if (!valid) {
    error.value = kind.value === 'email' ? '请输入有效的邮箱地址' : '请输入有效的中国大陆手机号'
    return
  }
  if (!captcha.value || !/^[A-Za-z0-9]{5}$/.test(form.captchaAnswer.trim())) {
    error.value = '请输入图中的 5 位验证码'
    return
  }

  sending.value = true
  try {
    const result = await api.sendContactCode(kind.value, contact, captcha.value.id, form.captchaAnswer.trim())
    sentTo.value = contact
    form.verificationCode = ''
    feedback.value = result.message
    const resendAt = Date.now() + result.resendAfterSeconds * 1000
    cooldown.value = result.resendAfterSeconds
    if (cooldown.value > 0) {
      timer = setInterval(() => {
        cooldown.value = Math.ceil((resendAt - Date.now()) / 1000)
        if (cooldown.value <= 0) stopCountdown()
      }, 1000)
    }
  } catch (caught) {
    error.value = messageFor(caught, '验证码发送失败，请稍后重试')
  } finally {
    sending.value = false
    void loadCaptcha()
  }
  if (sent.value && !error.value) {
    await nextTick()
    codeInput.value?.focus()
  }
}

async function confirmBinding() {
  if (sending.value || binding.value) return
  error.value = ''
  if (!/^[0-9]{6}$/.test(form.verificationCode.trim())) {
    error.value = '请输入收到的 6 位验证码'
    return
  }
  if (!form.currentPassword) {
    error.value = '请输入当前密码'
    return
  }

  binding.value = true
  try {
    await auth.bindContact(kind.value, target.value, form.verificationCode.trim(), form.currentPassword)
    showToast(`${label.value}绑定成功`, 'success')
    dialog.value?.close()
    emit('bound', kind.value)
  } catch (caught) {
    error.value = messageFor(caught, '绑定失败，请稍后重试')
    form.currentPassword = ''
  } finally {
    binding.value = false
  }
}
</script>

<template>
  <dialog
    ref="dialog"
    class="contact-dialog"
    aria-labelledby="contact-dialog-title"
    aria-describedby="contact-dialog-description"
    @close="reset"
    @cancel="(event) => { if (sending || binding) event.preventDefault() }"
  >
    <header class="contact-dialog-heading">
      <div>
        <h2 id="contact-dialog-title">绑定{{ label }}</h2>
        <p id="contact-dialog-description">绑定后可使用该{{ label }}登录。</p>
      </div>
      <button
        class="btn btn-ghost dialog-close"
        type="button"
        aria-label="关闭"
        :disabled="sending || binding"
        @click="dialog?.close()"
      >
        <AppIcon name="close" :size="20" />
      </button>
    </header>

    <form class="contact-form" novalidate :aria-busy="sending || binding" @submit.prevent="confirmBinding">
      <label class="field">
        <span class="field-label">{{ label }}</span>
        <span class="input-shell">
          <AppIcon :name="kind === 'email' ? 'mail' : 'smartphone'" :size="17" />
          <input
            v-model="form.contact"
            :name="kind"
            :type="kind === 'email' ? 'email' : 'tel'"
            :autocomplete="kind === 'email' ? 'email' : 'tel'"
            :placeholder="kind === 'email' ? 'name@example.com' : '中国大陆手机号'"
            :maxlength="kind === 'email' ? 100 : 14"
            :disabled="sending || binding"
            autofocus
            required
            @input="contactChanged"
          />
        </span>
      </label>

      <div class="field">
        <label class="field-label" for="contact-captcha">图形验证码</label>
        <div class="contact-input-row">
          <span class="input-shell">
            <input
              id="contact-captcha"
              v-model="form.captchaAnswer"
              type="text"
              autocomplete="off"
              :disabled="sending || binding"
              maxlength="5"
              placeholder="输入 5 位字符"
            />
          </span>
          <button
            class="btn btn-secondary captcha-preview"
            type="button"
            aria-label="刷新图形验证码"
            :disabled="captchaLoading || sending || binding"
            @click="loadCaptcha"
          >
            <img v-if="captcha" :src="captcha.image" alt="图形验证码" />
            <span v-else>{{ captchaLoading ? '加载中…' : '刷新图片' }}</span>
          </button>
        </div>
        <span v-if="captchaError" class="field-error" role="status">{{ captchaError }}</span>
      </div>

      <button
        class="btn btn-secondary"
        type="button"
        :disabled="sending || binding || captchaLoading || cooldown > 0 || !captcha"
        @click="sendCode"
      >
        {{ sending ? '发送中…' : cooldown > 0 ? `${cooldown} 秒后重发` : `发送${kind === 'email' ? '邮件' : '短信'}验证码` }}
      </button>
      <p v-if="feedback" class="contact-feedback" role="status">{{ feedback }}</p>

      <label class="field">
        <span class="field-label">{{ kind === 'email' ? '邮箱' : '短信' }}验证码</span>
        <span class="input-shell">
          <input
            ref="codeInput"
            v-model="form.verificationCode"
            inputmode="numeric"
            autocomplete="one-time-code"
            maxlength="6"
            :disabled="binding || sending"
            required
          />
        </span>
      </label>
      <label class="field">
        <span class="field-label">当前密码</span>
        <span class="input-shell">
          <AppIcon name="lock" :size="17" />
          <input
            v-model="form.currentPassword"
            type="password"
            autocomplete="current-password"
            maxlength="128"
            :disabled="binding || sending"
            required
          />
        </span>
      </label>

      <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
      <button class="btn btn-primary" type="submit" :disabled="binding || sending">
        {{ binding ? '绑定中…' : '确认绑定' }}
      </button>
    </form>
  </dialog>
</template>

<style scoped>
.contact-dialog {
  position: fixed;
  inset: 0;
  width: min(440px, calc(100% - 2rem));
  max-height: calc(100dvh - 2rem);
  margin: auto;
  padding: clamp(1rem, 5vw, 1.6rem);
  overflow: auto;
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  background: var(--surface-solid);
  color: var(--ink);
  box-shadow: var(--shadow-soft);
}

.contact-dialog::backdrop {
  background: rgb(0 0 0 / 65%);
  backdrop-filter: blur(4px);
}

.contact-dialog-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.contact-dialog-heading h2 {
  font-size: 1.25rem;
}

.contact-dialog-heading p,
.contact-feedback {
  margin-top: 0.25rem;
  color: var(--muted);
  font-size: 0.875rem;
}

.contact-form {
  display: grid;
  gap: 0.85rem;
}

.dialog-close,
.contact-form .btn {
  min-height: 44px;
}

.dialog-close {
  width: 44px;
  padding: 0;
}

.contact-input-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(96px, 36%);
  gap: 0.5rem;
}

.contact-input-row .captcha-preview {
  padding: 0;
  overflow: hidden;
}

.captcha-preview img {
  display: block;
  width: 100%;
  height: 44px;
  object-fit: contain;
}
</style>
