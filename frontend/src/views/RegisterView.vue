<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AuthLayout from '@/components/AuthLayout.vue'
import AppIcon from '@/components/AppIcon.vue'
import { ApiError } from '@/lib/api'
import { auth } from '@/stores/auth'
import { showToast } from '@/lib/toast'

const route = useRoute()
const router = useRouter()
const form = reactive({
  nickname: '',
  email: '',
  password: '',
  confirmPassword: '',
})
const showPassword = ref(false)
const loading = ref(false)
const error = ref('')
const fieldErrors = ref<Record<string, string>>({})

const redirectTarget = computed(() => {
  const value = route.query.redirect
  return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//') ? value : null
})
const loginLink = computed(() => ({
  name: 'login',
  query: redirectTarget.value ? { redirect: redirectTarget.value } : {},
}))
const passwordStrength = computed(() => {
  let score = 0
  if (form.password.length >= 6) score++
  if (/[A-Za-z]/.test(form.password) && /\d/.test(form.password)) score++
  if (form.password.length >= 10) score++
  if (/[^A-Za-z0-9]/.test(form.password)) score++
  const labels = ['软乎乎', '小猫爪', '渐入佳境', '星光守护', '传说级']
  return { score, label: labels[score] || '软乎乎' }
})

function validate() {
  const errors: Record<string, string> = {}
  const nickname = form.nickname.trim()
  if (!nickname || nickname.length > 20) errors.nickname = '昵称长度需为 1-20 个字符'
  if (!/^\S+@\S+\.\S+$/.test(form.email)) errors.email = '请输入有效的邮箱'
  else if (form.email.trim().length > 100) errors.email = '邮箱不能超过 100 个字符'
  if (form.password.length < 6 || form.password.length > 30) errors.password = '密码长度需为 6-30 个字符'
  if (form.confirmPassword !== form.password) errors.confirmPassword = '两次输入的密码不一致'
  fieldErrors.value = errors
  return Object.keys(errors).length === 0
}

async function submit() {
  if (loading.value || !validate()) return
  error.value = ''
  loading.value = true
  try {
    await auth.register({
      nickname: form.nickname.trim(),
      email: form.email.trim(),
      password: form.password,
    })
    showToast('契约成立，欢迎来到梦幻网络喵！', 'success')
    if (redirectTarget.value) {
      await router.replace(redirectTarget.value)
    } else {
      await router.replace({ name: 'login', query: { reason: 'registered' } })
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

    <form class="auth-form" novalidate @submit.prevent="submit">
      <label class="field">
        <span class="field-label">昵称 <small>1-20 个字符，可重复</small></span>
        <span class="input-shell" :class="{ 'has-error': fieldErrors.nickname }">
          <AppIcon name="spark" :size="18" />
          <input v-model="form.nickname" name="nickname" type="text" autocomplete="nickname" placeholder="星野小梦" maxlength="20" required />
        </span>
        <span v-if="fieldErrors.nickname" class="field-error">{{ fieldErrors.nickname }}</span>
      </label>

      <label class="field">
        <span class="field-label">邮箱地址</span>
        <span class="input-shell" :class="{ 'has-error': fieldErrors.email }">
          <AppIcon name="mail" :size="18" />
          <input v-model="form.email" name="email" type="email" autocomplete="email" placeholder="you@fantasy.network" maxlength="100" required />
        </span>
        <span v-if="fieldErrors.email" class="field-error">{{ fieldErrors.email }}</span>
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

      <button class="btn btn-primary btn-block" type="submit" :disabled="loading">
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
  color: #8a789e;
  font-size: 0.56rem;
}

.strength-label[data-score='1'] { color: #ff92b4; }
.strength-label[data-score='2'] { color: #ffc26f; }
.strength-label[data-score='3'] { color: #a4df8a; }
.strength-label[data-score='4'] { color: #77f1df; }

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

.password-meter[data-score='1'] i:nth-child(-n+1) { background: #ff92b4; }
.password-meter[data-score='2'] i:nth-child(-n+2) { background: #ffc26f; }
.password-meter[data-score='3'] i:nth-child(-n+3) { background: #a4df8a; }
.password-meter[data-score='4'] i { background: #77f1df; }

.contract-note {
  display: flex;
  align-items: flex-start;
  gap: 0.55rem;
  padding: 0.65rem 0.72rem;
  border: 1px solid rgba(255, 196, 234, 0.11);
  border-radius: 11px;
  color: #8e7ca6;
  background: rgba(255, 214, 245, 0.035);
  font-size: 0.58rem;
  line-height: 1.6;
}

.contract-paw {
  flex: none;
  filter: grayscale(0.35);
}
</style>
