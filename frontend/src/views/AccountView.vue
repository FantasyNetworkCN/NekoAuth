<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import AppLogo from '@/components/AppLogo.vue'
import { ApiError, api } from '@/lib/api'
import { browserName, formatDate, initials } from '@/lib/format'
import { showToast } from '@/lib/toast'
import { auth } from '@/stores/auth'
import type { Session } from '@/types'

const router = useRouter()
const user = computed(() => auth.state.user)
const displayName = computed(() => user.value?.displayName || user.value?.username || 'Neko')
const avatarText = computed(() => initials(displayName.value))

const sessions = ref<Session[]>([])
const sessionsLoading = ref(true)
const sessionError = ref('')
const revokingId = ref<number | null>(null)
const revokingAll = ref(false)
const loggingOut = ref(false)

const passwordForm = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
})
const passwordLoading = ref(false)
const passwordError = ref('')
const passwordFieldErrors = ref<Record<string, string>>({})

function validatePasswordForm() {
  const errors: Record<string, string> = {}
  if (!passwordForm.currentPassword) errors.currentPassword = '请输入当前密码'
  if (passwordForm.newPassword.length < 6 || passwordForm.newPassword.length > 30) {
    errors.newPassword = '新密码长度需为 6-30 个字符'
  } else if (passwordForm.newPassword === passwordForm.currentPassword) {
    errors.newPassword = '新密码不能与当前密码相同'
  }
  if (passwordForm.confirmPassword !== passwordForm.newPassword) {
    errors.confirmPassword = '两次输入的新密码不一致'
  }
  passwordFieldErrors.value = errors
  return Object.keys(errors).length === 0
}

async function changePassword() {
  if (passwordLoading.value || !validatePasswordForm()) return
  passwordLoading.value = true
  passwordError.value = ''
  passwordFieldErrors.value = {}
  try {
    await api.changePassword(passwordForm.currentPassword, passwordForm.newPassword)
    auth.clear()
    showToast('密码已更新，请使用新密码重新登录喵', 'success')
    await router.replace({ name: 'login', query: { reason: 'password-changed' } })
  } catch (caught) {
    if (caught instanceof ApiError && caught.code === 'INVALID_CURRENT_PASSWORD') {
      passwordFieldErrors.value = { currentPassword: caught.message }
    } else {
      passwordError.value = caught instanceof ApiError ? caught.message : '密码修改失败，请稍后重试'
    }
  } finally {
    passwordLoading.value = false
  }
}

async function loadSessions() {
  sessionsLoading.value = true
  sessionError.value = ''
  try {
    sessions.value = await api.sessions()
  } catch (caught) {
    sessionError.value = caught instanceof ApiError ? caught.message : '登录设备加载失败，请稍后重试'
  } finally {
    sessionsLoading.value = false
  }
}

async function revokeSession(session: Session) {
  if (revokingId.value !== null || revokingAll.value) return
  const prompt = session.current
    ? '撤销当前会话会立即退出登录，确定继续吗？'
    : `确定撤销「${browserName(session.userAgent)}」这次登录吗？`
  if (!window.confirm(prompt)) return

  revokingId.value = session.id
  sessionError.value = ''
  try {
    await api.revokeSession(session.id)
    if (session.current) {
      auth.clear()
      showToast('当前设备已安全退出喵', 'success')
      await router.replace({ name: 'login' })
      return
    }
    showToast('已撤销该设备的登录会话', 'success')
    await loadSessions()
  } catch (caught) {
    sessionError.value = caught instanceof ApiError ? caught.message : '会话撤销失败，请稍后重试'
  } finally {
    revokingId.value = null
  }
}

async function revokeAllSessions() {
  if (revokingAll.value || revokingId.value !== null) return
  if (!window.confirm('这会退出所有设备，包括当前设备。确定继续吗？')) return

  revokingAll.value = true
  sessionError.value = ''
  try {
    await api.revokeAllSessions()
    auth.clear()
    showToast('所有登录设备都已退出喵', 'success')
    await router.replace({ name: 'login' })
  } catch (caught) {
    sessionError.value = caught instanceof ApiError ? caught.message : '全部会话撤销失败，请稍后重试'
  } finally {
    revokingAll.value = false
  }
}

async function logout() {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    await auth.logout()
    showToast('已安全退出梦幻网络', 'success')
    await router.replace({ name: 'login' })
  } catch {
    showToast('退出请求失败，但本地登录状态已清除', 'info')
    await router.replace({ name: 'login' })
  } finally {
    loggingOut.value = false
  }
}

onMounted(() => {
  void loadSessions()
})
</script>

<template>
  <div class="account-page">
    <header class="account-topbar">
      <RouterLink to="/account" class="brand-link" aria-label="NekoAuth 账户中心">
        <AppLogo :size="35" />
      </RouterLink>
      <span class="account-mode">NEKO ID · ACCOUNT</span>
      <button class="btn btn-ghost btn-small" type="button" :disabled="loggingOut" @click="logout">
        <AppIcon name="logout" :size="15" />
        {{ loggingOut ? '退出中…' : '退出登录' }}
      </button>
    </header>

    <main v-if="user" class="account-main">
      <section class="account-intro">
        <div>
          <p class="account-eyebrow"><span></span> YOUR NEKO IDENTITY</p>
          <h1>欢迎回来，<em>{{ displayName }}</em></h1>
          <p>这里是你的最小化账户管理中心。身份资料保持只读，只保留密码与登录设备管理。</p>
        </div>
        <span class="intro-paw" aria-hidden="true">ฅ</span>
      </section>

      <div class="account-grid">
        <section class="panel identity-panel">
          <div class="panel-heading">
            <span class="panel-icon"><AppIcon name="user" :size="18" /></span>
            <div>
              <h2>账户资料</h2>
              <p>由 NekoAuth 统一管理</p>
            </div>
          </div>

          <div class="identity-summary">
            <span class="identity-avatar">{{ avatarText }}</span>
            <div>
              <strong>{{ displayName }}</strong>
              <span>@{{ user.username }}</span>
            </div>
            <span class="status-badge" :class="{ disabled: !user.enabled }">
              <i></i>{{ user.enabled ? '正常' : '已停用' }}
            </span>
          </div>

          <dl class="identity-list">
            <div>
              <dt>邮箱</dt>
              <dd>{{ user.email }}</dd>
            </div>
            <div>
              <dt>用户 ID</dt>
              <dd>{{ user.id }}</dd>
            </div>
            <div>
              <dt>加入时间</dt>
              <dd>{{ formatDate(user.createdAt) }}</dd>
            </div>
            <div>
              <dt>最近登录</dt>
              <dd>{{ formatDate(user.lastLoginAt) }}</dd>
            </div>
          </dl>

          <p class="readonly-note"><AppIcon name="lock" :size="14" /> 邮箱与显示名称暂不支持自助修改。</p>
        </section>

        <section class="panel password-panel">
          <div class="panel-heading">
            <span class="panel-icon"><AppIcon name="key" :size="18" /></span>
            <div>
              <h2>修改密码</h2>
              <p>更新后会退出所有设备</p>
            </div>
          </div>

          <div v-if="passwordError" class="alert alert-error" role="alert">
            <AppIcon name="alert" :size="17" />
            <span>{{ passwordError }}</span>
          </div>

          <form class="auth-form password-form" novalidate @submit.prevent="changePassword">
            <label class="field">
              <span class="field-label">当前密码</span>
              <span class="input-shell" :class="{ 'has-error': passwordFieldErrors.currentPassword }">
                <AppIcon name="lock" :size="17" />
                <input
                  v-model="passwordForm.currentPassword"
                  name="currentPassword"
                  type="password"
                  autocomplete="current-password"
                  maxlength="128"
                  required
                />
              </span>
              <span v-if="passwordFieldErrors.currentPassword" class="field-error">{{ passwordFieldErrors.currentPassword }}</span>
            </label>

            <label class="field">
              <span class="field-label">新密码</span>
              <span class="input-shell" :class="{ 'has-error': passwordFieldErrors.newPassword }">
                <AppIcon name="key" :size="17" />
                <input
                  v-model="passwordForm.newPassword"
                  name="newPassword"
                  type="password"
                  autocomplete="new-password"
                  placeholder="6-30 个字符"
                  maxlength="30"
                  required
                />
              </span>
              <span v-if="passwordFieldErrors.newPassword" class="field-error">{{ passwordFieldErrors.newPassword }}</span>
            </label>

            <label class="field">
              <span class="field-label">确认新密码</span>
              <span class="input-shell" :class="{ 'has-error': passwordFieldErrors.confirmPassword }">
                <AppIcon name="shield-cat" :size="17" />
                <input
                  v-model="passwordForm.confirmPassword"
                  name="confirmPassword"
                  type="password"
                  autocomplete="new-password"
                  placeholder="再输入一次新密码"
                  maxlength="30"
                  required
                />
              </span>
              <span v-if="passwordFieldErrors.confirmPassword" class="field-error">{{ passwordFieldErrors.confirmPassword }}</span>
            </label>

            <button class="btn btn-primary btn-block" type="submit" :disabled="passwordLoading">
              <span v-if="passwordLoading" class="spinner"></span>
              <template v-else>
                <AppIcon name="shield-cat" :size="16" />
                更新密码并退出所有设备
              </template>
            </button>
          </form>
        </section>
      </div>

      <section class="panel sessions-panel">
        <div class="sessions-heading">
          <div class="panel-heading">
            <span class="panel-icon"><AppIcon name="devices" :size="18" /></span>
            <div>
              <h2>登录设备</h2>
              <p>{{ sessions.length }} 个活跃会话</p>
            </div>
          </div>
          <button
            class="btn btn-danger btn-small"
            type="button"
            :disabled="sessionsLoading || sessions.length === 0 || revokingAll || revokingId !== null"
            @click="revokeAllSessions"
          >
            <AppIcon name="logout" :size="14" />
            {{ revokingAll ? '退出中…' : '退出全部设备' }}
          </button>
        </div>

        <div v-if="sessionError" class="alert alert-error" role="alert">
          <AppIcon name="alert" :size="17" />
          <span>{{ sessionError }}</span>
          <button class="btn btn-ghost btn-small retry-button" type="button" @click="loadSessions">重试</button>
        </div>

        <div v-if="sessionsLoading" class="sessions-loading">
          <span class="spinner"></span>
          正在整理猫爪登录记录…
        </div>

        <div v-else-if="sessions.length === 0 && !sessionError" class="sessions-empty">
          <AppIcon name="monitor" :size="24" />
          <span>当前没有可显示的活跃会话</span>
        </div>

        <div v-else class="session-list">
          <article v-for="session in sessions" :key="session.id" class="session-row">
            <span class="session-icon"><AppIcon name="monitor" :size="19" /></span>
            <div class="session-copy">
              <div class="session-title">
                <strong>{{ browserName(session.userAgent) }}</strong>
                <span v-if="session.current" class="current-badge">当前设备</span>
              </div>
              <p>{{ session.ipAddress || '未知 IP' }} · 最近活动 {{ formatDate(session.lastUsedAt || session.createdAt) }}</p>
              <small>登录于 {{ formatDate(session.createdAt) }} · 有效至 {{ formatDate(session.expiresAt) }}</small>
            </div>
            <button
              class="btn btn-ghost btn-small session-revoke"
              type="button"
              :disabled="revokingId !== null || revokingAll"
              @click="revokeSession(session)"
            >
              {{ revokingId === session.id ? '处理中…' : session.current ? '退出' : '撤销' }}
            </button>
          </article>
        </div>
      </section>

      <footer class="account-footnote">
        <AppIcon name="shield" :size="14" />
        <span>访问令牌只保存在当前页面内存，刷新令牌由 HttpOnly Cookie 保护。</span>
      </footer>
    </main>
  </div>
</template>

<style scoped>
.account-page {
  position: relative;
  min-height: 100svh;
  padding-bottom: 3rem;
  background:
    radial-gradient(circle at 88% 4%, rgba(246, 141, 202, 0.14), transparent 30%),
    radial-gradient(circle at 5% 86%, rgba(94, 231, 214, 0.07), transparent 30%);
}

.account-topbar {
  position: sticky;
  z-index: 20;
  top: 0;
  display: flex;
  min-height: 68px;
  align-items: center;
  gap: 1rem;
  padding: 0.8rem clamp(1rem, 4vw, 2.4rem);
  border-bottom: 1px solid rgba(255, 203, 239, 0.09);
  background: rgba(16, 11, 29, 0.82);
  backdrop-filter: blur(18px);
}

.brand-link { display: inline-flex; flex: none; }

.account-mode {
  flex: 1;
  color: #78638d;
  font-size: 0.52rem;
  font-weight: 750;
  letter-spacing: 0.15em;
}

.account-main {
  width: min(1080px, calc(100% - 2rem));
  margin: 0 auto;
  padding-top: clamp(1.7rem, 4vw, 3.2rem);
}

.account-intro {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1.5rem;
  margin-bottom: 1.6rem;
}

.account-eyebrow {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.6rem;
  color: #ffadd8;
  font-size: 0.57rem;
  font-weight: 800;
  letter-spacing: 0.15em;
}

.account-eyebrow span {
  width: 22px;
  height: 1px;
  background: linear-gradient(90deg, #ff9fd1, transparent);
}

.account-intro h1 {
  color: #fff6fd;
  font-size: clamp(1.7rem, 4vw, 2.65rem);
  font-weight: 820;
  letter-spacing: -0.06em;
  line-height: 1.08;
}

.account-intro h1 em { color: #ffa5d8; font-style: normal; }

.account-intro p:not(.account-eyebrow) {
  max-width: 560px;
  margin-top: 0.65rem;
  color: #9682a8;
  font-size: 0.72rem;
  line-height: 1.75;
}

.intro-paw {
  flex: none;
  color: rgba(255, 170, 219, 0.42);
  font-size: clamp(3rem, 8vw, 5.4rem);
  line-height: 0.8;
  transform: rotate(-10deg);
}

.account-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(0, 1.08fr);
  gap: 1rem;
}

.panel {
  border: 1px solid rgba(255, 203, 239, 0.11);
  border-radius: 20px;
  background:
    linear-gradient(145deg, rgba(255, 239, 251, 0.045), transparent 44%),
    rgba(24, 16, 40, 0.8);
  box-shadow: 0 24px 68px rgba(3, 1, 12, 0.28), inset 0 1px 0 rgba(255, 255, 255, 0.045);
  backdrop-filter: blur(16px);
}

.identity-panel,
.password-panel { padding: 1.25rem; }

.panel-heading {
  display: flex;
  align-items: center;
  gap: 0.65rem;
}

.panel-heading > div { display: grid; gap: 0.12rem; }

.panel-icon {
  display: grid;
  width: 36px;
  height: 36px;
  flex: none;
  place-items: center;
  border: 1px solid rgba(255, 181, 226, 0.12);
  border-radius: 11px;
  color: #ffb2df;
  background: rgba(255, 156, 214, 0.075);
}

.panel-heading h2 { color: #f9eaf6; font-size: 0.84rem; font-weight: 720; }
.panel-heading p { color: #776486; font-size: 0.58rem; }

.identity-summary {
  display: flex;
  align-items: center;
  gap: 0.72rem;
  margin-top: 1.25rem;
  padding: 0.8rem;
  border: 1px solid rgba(255, 204, 239, 0.08);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.025);
}

.identity-avatar {
  display: grid;
  width: 50px;
  height: 50px;
  flex: none;
  place-items: center;
  border: 1px solid rgba(255, 200, 238, 0.22);
  border-radius: 15px;
  color: #4a235d;
  background: linear-gradient(145deg, #ffe8f8, #eaa7d8 56%, #a98bf0);
  font-size: 0.92rem;
  font-weight: 850;
  box-shadow: 0 8px 22px rgba(210, 95, 176, 0.18);
}

.identity-summary > div { display: grid; min-width: 0; flex: 1; gap: 0.16rem; }
.identity-summary strong { overflow: hidden; color: #fff5fc; font-size: 0.82rem; font-weight: 720; text-overflow: ellipsis; white-space: nowrap; }
.identity-summary > div span { overflow: hidden; color: #8e789f; font-size: 0.6rem; text-overflow: ellipsis; white-space: nowrap; }

.status-badge,
.current-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  flex: none;
  padding: 0.25rem 0.5rem;
  border: 1px solid rgba(110, 236, 210, 0.14);
  border-radius: 999px;
  color: #86e8d5;
  background: rgba(76, 219, 192, 0.055);
  font-size: 0.53rem;
  font-weight: 650;
}

.status-badge i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #7cf0dc;
  box-shadow: 0 0 7px #7cf0dc;
}

.status-badge.disabled { color: #ff9baa; background: rgba(255, 107, 129, 0.07); }
.status-badge.disabled i { background: #ff829f; box-shadow: none; }

.identity-list {
  display: grid;
  gap: 0;
  margin-top: 1rem;
}

.identity-list > div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.66rem 0;
  border-bottom: 1px dashed rgba(255, 208, 241, 0.075);
}

.identity-list > div:last-child { border-bottom: 0; }
.identity-list dt { color: #786487; font-size: 0.61rem; }
.identity-list dd { overflow: hidden; color: #decbe3; font-size: 0.65rem; font-weight: 560; text-align: right; text-overflow: ellipsis; white-space: nowrap; }

.readonly-note {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-top: 0.85rem;
  padding-top: 0.85rem;
  border-top: 1px solid rgba(255, 208, 241, 0.07);
  color: #73617f;
  font-size: 0.57rem;
  line-height: 1.55;
}

.readonly-note svg { flex: none; }

.password-form { margin-top: 1rem; gap: 0.82rem; }
.password-form .field-label { font-size: 0.63rem; }
.password-form .input-shell { min-height: 42px; }

.sessions-panel {
  margin-top: 1rem;
  padding: 1.25rem;
}

.sessions-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.sessions-loading,
.sessions-empty {
  display: flex;
  min-height: 100px;
  align-items: center;
  justify-content: center;
  gap: 0.55rem;
  color: #796787;
  font-size: 0.65rem;
}

.sessions-empty { flex-direction: column; }

.session-list {
  display: grid;
  margin-top: 1rem;
  border-top: 1px solid rgba(255, 208, 241, 0.07);
}

.session-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.9rem 0;
  border-bottom: 1px solid rgba(255, 208, 241, 0.065);
}

.session-row:last-child { border-bottom: 0; padding-bottom: 0; }

.session-icon {
  display: grid;
  width: 38px;
  height: 38px;
  flex: none;
  place-items: center;
  border: 1px solid rgba(140, 226, 216, 0.1);
  border-radius: 11px;
  color: #81d9cf;
  background: rgba(79, 207, 192, 0.055);
}

.session-copy { display: grid; min-width: 0; flex: 1; gap: 0.16rem; }
.session-title { display: flex; align-items: center; gap: 0.45rem; }
.session-title strong { overflow: hidden; color: #e7d8e9; font-size: 0.68rem; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.current-badge { border-color: rgba(255, 177, 224, 0.14); color: #ffb9e1; background: rgba(255, 145, 210, 0.06); }
.session-copy p,
.session-copy small { overflow: hidden; color: #766382; font-size: 0.56rem; text-overflow: ellipsis; white-space: nowrap; }
.session-copy small { color: #63526f; font-size: 0.52rem; }
.session-revoke { flex: none; color: #b78d9d; }

.retry-button { margin-left: auto; flex: none; }

.account-footnote {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  margin-top: 1.2rem;
  color: #655474;
  font-size: 0.55rem;
  line-height: 1.6;
  text-align: center;
}

.account-footnote svg { flex: none; }

@media (max-width: 780px) {
  .account-grid { grid-template-columns: 1fr; }
  .account-intro { align-items: flex-start; }
  .intro-paw { display: none; }
}

@media (max-width: 560px) {
  .account-topbar { gap: 0.6rem; padding-inline: 1rem; }
  .account-mode { display: none; }
  .account-main { width: min(100% - 1.2rem, 1080px); }
  .identity-panel,
  .password-panel,
  .sessions-panel { padding: 1rem; border-radius: 17px; }
  .sessions-heading { align-items: flex-start; flex-direction: column; }
  .sessions-heading > .btn { width: 100%; }
  .session-row { align-items: flex-start; }
  .session-copy p,
  .session-copy small { white-space: normal; }
}
</style>
