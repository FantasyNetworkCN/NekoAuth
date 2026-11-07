<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import AppLogo from '@/components/AppLogo.vue'
import accountNeko from '@/assets/local/account-neko.webp'
import { ApiError, api } from '@/lib/api'
import { deviceDetails, deviceLabel, formatDate, initials } from '@/lib/format'
import { showToast } from '@/lib/toast'
import { auth } from '@/stores/auth'
import type { Session } from '@/types'

const router = useRouter()
const user = computed(() => auth.state.user)
const displayName = computed(() => user.value?.displayName || user.value?.username || 'Neko')
const avatarText = computed(() => initials(displayName.value))
const roleLabel = computed(() => user.value?.role === 'ADMIN' ? '管理员' : '普通成员')
const avatarRevision = ref(0)
const avatarBroken = ref(false)
const avatarUploading = ref(false)
const avatarInput = ref<HTMLInputElement | null>(null)
const avatarSrc = computed(() => {
  const base = user.value?.avatarUrl || ''
  if (!base) return ''
  return avatarRevision.value > 0 ? `${base}?v=${avatarRevision.value}` : base
})

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

const profileForm = reactive({ nickname: user.value?.nickname || user.value?.displayName || '' })
const profileLoading = ref(false)
const profileError = ref('')
const profileFieldErrors = ref<Record<string, string>>({})

function deviceIcon(session: Session): string {
  if (session.device?.type === 'mobile') return 'smartphone'
  if (session.device?.type === 'tablet') return 'tablet'
  return 'monitor'
}

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

function validateNickname() {
  const nickname = profileForm.nickname.trim()
  if (!nickname) {
    profileFieldErrors.value = { nickname: '请输入昵称' }
    return false
  }
  if (nickname.length > 32) {
    profileFieldErrors.value = { nickname: '昵称不能超过 32 个字符' }
    return false
  }
  if (/[\u0000-\u001f\u007f-\u009f]/.test(nickname)) {
    profileFieldErrors.value = { nickname: '昵称不能包含控制字符' }
    return false
  }
  profileFieldErrors.value = {}
  return true
}

async function saveNickname() {
  if (profileLoading.value || !validateNickname()) return
  profileLoading.value = true
  profileError.value = ''
  try {
    const updated = await auth.updateProfile(profileForm.nickname.trim())
    profileForm.nickname = updated.nickname || updated.displayName
    showToast('昵称已经更新喵', 'success')
  } catch (caught) {
    if (caught instanceof ApiError && caught.fields.nickname) {
      profileFieldErrors.value = { nickname: caught.fields.nickname }
    } else {
      profileError.value = caught instanceof ApiError ? caught.message : '昵称更新失败，请稍后重试'
    }
  } finally {
    profileLoading.value = false
  }
}

async function uploadAvatar(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || avatarUploading.value) return
  if (!['image/png', 'image/jpeg', 'image/gif', 'image/webp', 'image/bmp'].includes(file.type)) {
    showToast('头像仅支持 PNG、JPEG、GIF、WebP 或 BMP', 'error')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    showToast('头像不能超过 10MiB', 'error')
    return
  }
  avatarUploading.value = true
  try {
    await auth.uploadAvatar(file)
    avatarBroken.value = false
    avatarRevision.value = Date.now()
    showToast('头像已经更新喵', 'success')
  } catch (caught) {
    showToast(caught instanceof ApiError ? caught.message : '头像上传失败，请稍后重试', 'error')
  } finally {
    avatarUploading.value = false
  }
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
    : `确定撤销「${deviceLabel(session)}」这次登录吗？`
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
      <div class="topbar-inner">
        <RouterLink to="/account" class="brand-link" aria-label="梦幻网络账户中心">
          <AppLogo :size="34" />
        </RouterLink>
        <span class="account-mode"><b>/</b> NEKO ID · ACCOUNT CENTER</span>
        <div class="topbar-actions">
          <span class="session-state"><i></i> SECURE SESSION</span>
          <button class="btn btn-secondary btn-small" type="button" :disabled="loggingOut" @click="logout">
            <AppIcon name="logout" :size="15" />
            {{ loggingOut ? '退出中…' : '退出登录' }}
          </button>
        </div>
      </div>
    </header>

    <main v-if="user" class="account-main">
      <section class="account-overview" aria-labelledby="account-title">
        <div class="overview-copy">
          <p class="account-eyebrow"><span></span> YOUR NEKO IDENTITY</p>
          <h1 id="account-title">欢迎回来，<em>{{ displayName }}</em></h1>
          <p>这里是你的 Neko ID 控制台。管理公开昵称、账户安全与所有登录设备，所有变更都会立即同步。</p>
          <div class="overview-tags">
            <span><AppIcon name="user" :size="13" /> @{{ user.username }}</span>
            <span><AppIcon name="shield-cat" :size="13" /> {{ roleLabel }}</span>
            <span><AppIcon name="lock" :size="13" /> Neko ID #{{ user.id }}</span>
          </div>
        </div>

        <article class="overview-profile">
          <img class="overview-art" :src="accountNeko" alt="" />
          <div class="overview-shade" aria-hidden="true"></div>
          <div class="overview-profile-top">
            <img
              v-if="avatarSrc && !avatarBroken"
              class="identity-avatar identity-avatar--image"
              :src="avatarSrc"
              :alt="displayName"
              @error="avatarBroken = true"
            />
            <span v-else class="identity-avatar">{{ avatarText }}</span>
            <span class="status-badge" :class="{ disabled: !user.enabled }">
              <i></i>{{ user.enabled ? '账户正常' : '已停用' }}
            </span>
          </div>
          <div class="overview-profile-copy">
            <small>NEKO ID CARD</small>
            <strong>{{ displayName }}</strong>
            <span>@{{ user.username }}</span>
          </div>
        </article>
      </section>

      <div class="account-layout">
        <div class="account-stack">
          <section class="panel profile-panel">
            <header class="section-heading">
              <span class="panel-icon"><AppIcon name="user" :size="18" /></span>
              <div>
                <p class="panel-kicker">PUBLIC PROFILE</p>
                <h2>账户资料</h2>
                <p>昵称会展示在梦幻网络的各个应用中。</p>
              </div>
            </header>

            <div v-if="profileError" class="alert alert-error" role="alert">
              <AppIcon name="alert" :size="17" />
              <span>{{ profileError }}</span>
            </div>

            <div class="avatar-editor">
              <img
                v-if="avatarSrc && !avatarBroken"
                class="avatar-editor__preview"
                :src="avatarSrc"
                :alt="displayName"
                @error="avatarBroken = true"
              />
              <span v-else class="avatar-editor__preview avatar-editor__preview--fallback">{{ avatarText }}</span>
              <div class="avatar-editor__copy">
                <strong>账户头像</strong>
                <span>头像会同步展示在 NekoMusic 等已接入应用。</span>
              </div>
              <input
                ref="avatarInput"
                class="avatar-editor__file"
                type="file"
                accept="image/png,image/jpeg,image/gif,image/webp,image/bmp"
                @change="uploadAvatar"
              />
              <button
                class="btn btn-secondary btn-small"
                type="button"
                :disabled="avatarUploading"
                @click="avatarInput?.click()"
              >
                <span v-if="avatarUploading" class="spinner"></span>
                <AppIcon v-else name="edit" :size="15" />
                {{ avatarUploading ? '上传中…' : '更换头像' }}
              </button>
            </div>

            <form class="profile-form" novalidate @submit.prevent="saveNickname">
              <label class="field">
                <span class="field-row">
                  <span class="field-label">昵称</span>
                  <span class="field-hint">1-32 个字符，不能包含违禁词</span>
                </span>
                <span class="input-shell" :class="{ 'has-error': profileFieldErrors.nickname }">
                  <AppIcon name="user" :size="17" />
                  <input
                    v-model="profileForm.nickname"
                    name="nickname"
                    type="text"
                    autocomplete="nickname"
                    placeholder="给自己起一个昵称"
                    maxlength="32"
                    required
                  />
                </span>
                <span v-if="profileFieldErrors.nickname" class="field-error">{{ profileFieldErrors.nickname }}</span>
              </label>

              <div class="form-actions">
                <span>保存后立即对已接入应用生效</span>
                <button class="btn btn-primary" type="submit" :disabled="profileLoading">
                  <span v-if="profileLoading" class="spinner"></span>
                  <template v-else>
                    <AppIcon name="check" :size="16" />
                    保存昵称
                  </template>
                </button>
              </div>
            </form>
          </section>

          <section class="panel security-panel">
            <header class="section-heading">
              <span class="panel-icon"><AppIcon name="key" :size="18" /></span>
              <div>
                <p class="panel-kicker">ACCOUNT SECURITY</p>
                <h2>修改密码</h2>
                <p>更新密码后，所有已登录设备都会立即退出。</p>
              </div>
            </header>

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

              <div class="password-grid">
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
              </div>

              <div class="form-actions">
                <span>修改后需要重新登录</span>
                <button class="btn btn-primary" type="submit" :disabled="passwordLoading">
                  <span v-if="passwordLoading" class="spinner"></span>
                  <template v-else>
                    <AppIcon name="shield-cat" :size="16" />
                    更新密码
                  </template>
                </button>
              </div>
            </form>
          </section>
        </div>

        <aside class="account-side">
          <section class="panel identity-panel">
            <header class="section-heading">
              <span class="panel-icon"><AppIcon name="spark" :size="18" /></span>
              <div>
                <p class="panel-kicker">IDENTITY DETAILS</p>
                <h2>身份信息</h2>
                <p>由梦幻网络统一签发与管理。</p>
              </div>
            </header>

            <dl class="identity-list">
              <div>
                <dt>邮箱</dt>
                <dd>{{ user.email }}</dd>
              </div>
              <div>
                <dt>用户名</dt>
                <dd>@{{ user.username }}</dd>
              </div>
              <div>
                <dt>成员角色</dt>
                <dd>{{ roleLabel }}</dd>
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

            <p class="readonly-note">
              <AppIcon name="lock" :size="14" />
              邮箱与用户名暂不支持自助修改，请联系管理员。
            </p>
          </section>

          <section class="security-note">
            <span><AppIcon name="shield" :size="20" /></span>
            <div>
              <strong>会话由 HttpOnly Cookie 保护</strong>
              <p>访问令牌只保存在当前页面内存中，不写入 localStorage。</p>
            </div>
          </section>
        </aside>

        <section class="panel sessions-panel">
          <div class="sessions-heading">
            <header class="section-heading">
              <span class="panel-icon"><AppIcon name="devices" :size="18" /></span>
              <div>
                <p class="panel-kicker">ACTIVE SESSIONS</p>
                <h2>登录设备</h2>
                <p>{{ sessions.length }} 个活跃会话，可随时单独撤销。</p>
              </div>
            </header>
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
            正在读取登录设备…
          </div>

          <div v-else-if="sessions.length === 0 && !sessionError" class="sessions-empty">
            <AppIcon name="monitor" :size="24" />
            <span>当前没有可显示的活跃会话</span>
          </div>

          <div v-else class="session-list">
            <article v-for="session in sessions" :key="session.id" class="session-row">
              <span class="session-icon"><AppIcon :name="deviceIcon(session)" :size="19" /></span>
              <div class="session-copy">
                <div class="session-title">
                  <strong>{{ deviceLabel(session) }}</strong>
                  <span v-if="session.current" class="current-badge">当前设备</span>
                </div>
                <p>{{ session.ipAddress || '未知 IP' }} · 最近活动 {{ formatDate(session.lastUsedAt || session.createdAt) }}</p>
                <small v-if="deviceDetails(session)">{{ deviceDetails(session) }}</small>
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
      </div>

      <footer class="account-footnote">
        <AppIcon name="shield" :size="14" />
        <span>Neko ID 会持续保护你的账户、密码与登录会话。</span>
      </footer>
    </main>
  </div>
</template>

<style scoped>
.account-page {
  min-height: 100svh;
  padding-bottom: 2.5rem;
  background:
    radial-gradient(circle at 88% 4%, rgba(239, 98, 98, 0.055), transparent 30%),
    radial-gradient(circle at 6% 80%, rgba(226, 180, 92, 0.035), transparent 32%);
}

.account-topbar {
  position: sticky;
  z-index: 20;
  top: 0;
  border-bottom: 1px solid var(--line);
  background: rgba(9, 9, 11, 0.86);
  backdrop-filter: blur(22px) saturate(120%);
}

.topbar-inner {
  display: flex;
  width: min(1180px, calc(100% - 2rem));
  min-height: 70px;
  align-items: center;
  gap: 1rem;
  margin: 0 auto;
}

.brand-link {
  display: inline-flex;
  flex: none;
}

.account-mode {
  flex: 1;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.51rem;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.account-mode b {
  margin-right: 0.35rem;
  color: var(--accent);
  font-weight: 700;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 0.65rem;
}

.session-state {
  display: inline-flex;
  min-height: 30px;
  align-items: center;
  gap: 0.4rem;
  padding: 0 0.65rem;
  border: 1px solid var(--line-soft);
  border-radius: var(--r-pill);
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.45rem;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.session-state i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--success);
  box-shadow: 0 0 8px rgba(121, 198, 155, 0.55);
}

.account-main {
  width: min(1180px, calc(100% - 2rem));
  margin: 0 auto;
  padding-top: clamp(1.5rem, 3vw, 2.6rem);
}

.account-overview {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(300px, 0.85fr);
  gap: 1rem;
  margin-bottom: 1rem;
}

.overview-copy {
  display: flex;
  min-height: 260px;
  flex-direction: column;
  justify-content: center;
  padding: clamp(1.5rem, 3.8vw, 3rem);
  border: 1px solid var(--line);
  border-radius: var(--r-xl);
  background: rgba(24, 24, 28, 0.78);
  box-shadow: var(--shadow-soft);
  backdrop-filter: blur(18px);
}

.account-eyebrow {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 0.56rem;
  font-weight: 700;
  letter-spacing: 0.15em;
}

.account-eyebrow span {
  width: 22px;
  height: 1px;
  background: linear-gradient(90deg, var(--accent), transparent);
}

.overview-copy h1 {
  margin-top: 0.75rem;
  color: var(--ink);
  font-size: clamp(2rem, 4vw, 3.2rem);
  font-weight: 820;
  letter-spacing: -0.06em;
  line-height: 1.06;
  text-wrap: balance;
}

.overview-copy h1 em {
  color: transparent;
  background: var(--gradient-text);
  background-clip: text;
  -webkit-background-clip: text;
  font-style: normal;
}

.overview-copy > p:not(.account-eyebrow) {
  max-width: 560px;
  margin-top: 0.8rem;
  color: var(--body);
  font-size: 0.73rem;
  line-height: 1.75;
}

.overview-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.42rem;
  margin-top: 1.15rem;
}

.overview-tags span {
  display: inline-flex;
  min-height: 30px;
  align-items: center;
  gap: 0.35rem;
  padding: 0 0.62rem;
  border: 1px solid var(--line-soft);
  border-radius: var(--r-pill);
  color: var(--muted);
  background: rgba(255, 255, 255, 0.025);
  font-size: 0.52rem;
}

.overview-profile {
  position: relative;
  min-height: 260px;
  margin: 0;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: var(--r-xl);
  background: #111114;
  box-shadow: var(--shadow-soft);
}

.overview-art {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: 50% 23%;
  filter: saturate(0.92) contrast(1.03) sepia(0.04);
}

.overview-shade {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(180deg, rgba(9, 9, 11, 0.15), transparent 38% 52%, rgba(9, 9, 11, 0.9)),
    linear-gradient(90deg, rgba(9, 9, 11, 0.3), transparent 55%);
}

.overview-profile-top {
  position: absolute;
  z-index: 1;
  inset: 0.85rem 0.85rem auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;
}

.identity-avatar {
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 15px;
  color: #fff7f4;
  background: rgba(20, 20, 24, 0.68);
  font-size: 0.82rem;
  font-weight: 800;
  backdrop-filter: blur(14px);
}

.identity-avatar--image {
  object-fit: cover;
}

.avatar-editor {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin: 1rem 0;
  padding: 0.75rem;
  border: 1px solid var(--line);
  border-radius: 0.9rem;
  background: var(--surface-soft);
}

.avatar-editor__preview {
  width: 54px;
  height: 54px;
  flex: none;
  border-radius: 0.8rem;
  object-fit: cover;
  border: 1px solid var(--line-strong);
  background: var(--surface);
}

.avatar-editor__preview--fallback {
  display: grid;
  place-items: center;
  color: var(--accent);
  font-size: 0.9rem;
  font-weight: 800;
}

.avatar-editor__copy {
  display: grid;
  flex: 1;
  min-width: 0;
  gap: 0.15rem;
}

.avatar-editor__copy strong {
  color: var(--ink);
  font-size: 0.74rem;
}

.avatar-editor__copy span {
  color: var(--muted);
  font-size: 0.58rem;
  line-height: 1.5;
}

.avatar-editor__file {
  display: none;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.36rem;
  padding: 0.28rem 0.55rem;
  border: 1px solid rgba(121, 198, 155, 0.24);
  border-radius: var(--r-pill);
  color: var(--success);
  background: rgba(20, 20, 24, 0.62);
  font-size: 0.52rem;
  font-weight: 650;
  backdrop-filter: blur(12px);
}

.status-badge i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--success);
  box-shadow: 0 0 8px rgba(121, 198, 155, 0.6);
}

.status-badge.disabled {
  border-color: rgba(255, 107, 107, 0.25);
  color: #ffaaaa;
}

.status-badge.disabled i {
  background: var(--danger);
  box-shadow: none;
}

.overview-profile-copy {
  position: absolute;
  z-index: 1;
  inset: auto 1rem 1rem;
  display: grid;
  gap: 0.08rem;
}

.overview-profile-copy small {
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 0.44rem;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.overview-profile-copy strong {
  color: #fff;
  font-size: 1.05rem;
  font-weight: 760;
}

.overview-profile-copy span {
  color: rgba(255, 255, 255, 0.65);
  font-size: 0.58rem;
}

.account-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 330px;
  gap: 1rem;
  align-items: start;
}

.account-stack {
  display: grid;
  gap: 1rem;
  min-width: 0;
}

.account-side {
  display: grid;
  gap: 1rem;
}

.panel {
  padding: clamp(1rem, 2vw, 1.35rem);
}

.section-heading {
  display: flex;
  align-items: flex-start;
  gap: 0.7rem;
}

.section-heading > div {
  min-width: 0;
}

.panel-icon {
  display: grid;
  width: 38px;
  height: 38px;
  flex: none;
  place-items: center;
  border: 1px solid rgba(239, 98, 98, 0.16);
  border-radius: 12px;
  color: var(--accent);
  background: rgba(239, 98, 98, 0.08);
}

.panel-kicker {
  margin-bottom: 0.18rem;
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 0.48rem;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.section-heading h2 {
  color: var(--ink);
  font-size: 0.96rem;
  font-weight: 720;
  letter-spacing: -0.02em;
}

.section-heading p:last-child {
  margin-top: 0.2rem;
  color: var(--muted);
  font-size: 0.6rem;
  line-height: 1.55;
}

.profile-form,
.password-form {
  display: grid;
  gap: 0.85rem;
  margin-top: 1.15rem;
}

.password-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.75rem;
}

.form-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding-top: 0.15rem;
}

.form-actions > span {
  color: var(--muted);
  font-size: 0.54rem;
}

.form-actions .btn {
  min-height: 38px;
  padding-inline: 0.9rem;
}

.identity-list {
  display: grid;
  margin-top: 1rem;
}

.identity-list > div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.68rem 0;
  border-bottom: 1px solid var(--line-soft);
}

.identity-list > div:last-child {
  border-bottom: 0;
}

.identity-list dt {
  color: var(--muted);
  font-size: 0.59rem;
}

.identity-list dd {
  overflow: hidden;
  max-width: 68%;
  color: var(--body);
  font-size: 0.64rem;
  font-weight: 560;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.readonly-note {
  display: flex;
  align-items: flex-start;
  gap: 0.4rem;
  margin-top: 0.85rem;
  padding-top: 0.85rem;
  border-top: 1px solid var(--line-soft);
  color: var(--muted);
  font-size: 0.55rem;
  line-height: 1.55;
}

.security-note {
  display: flex;
  align-items: flex-start;
  gap: 0.65rem;
  padding: 1rem;
  border: 1px solid rgba(121, 198, 155, 0.16);
  border-radius: var(--r-lg);
  background: rgba(121, 198, 155, 0.055);
}

.security-note > span {
  display: grid;
  width: 36px;
  height: 36px;
  flex: none;
  place-items: center;
  border-radius: 11px;
  color: var(--success);
  background: rgba(121, 198, 155, 0.1);
}

.security-note strong {
  display: block;
  color: #b9dfc7;
  font-size: 0.64rem;
  font-weight: 650;
}

.security-note p {
  margin-top: 0.2rem;
  color: #83a590;
  font-size: 0.54rem;
  line-height: 1.6;
}

.sessions-panel {
  grid-column: 1 / -1;
}

.sessions-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.sessions-loading,
.sessions-empty {
  display: flex;
  min-height: 120px;
  align-items: center;
  justify-content: center;
  gap: 0.55rem;
  color: var(--muted);
  font-size: 0.65rem;
}

.sessions-empty {
  flex-direction: column;
}

.session-list {
  display: grid;
  margin-top: 1rem;
  border-top: 1px solid var(--line-soft);
}

.session-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.9rem 0;
  border-bottom: 1px solid var(--line-soft);
}

.session-row:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.session-icon {
  display: grid;
  width: 40px;
  height: 40px;
  flex: none;
  place-items: center;
  border: 1px solid rgba(239, 98, 98, 0.13);
  border-radius: 12px;
  color: var(--accent);
  background: rgba(239, 98, 98, 0.065);
}

.session-copy {
  display: grid;
  min-width: 0;
  flex: 1;
  gap: 0.13rem;
}

.session-title {
  display: flex;
  align-items: center;
  gap: 0.45rem;
}

.session-title strong {
  overflow: hidden;
  color: var(--body);
  font-size: 0.69rem;
  font-weight: 660;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.current-badge {
  flex: none;
  padding: 0.12rem 0.36rem;
  border: 1px solid rgba(239, 98, 98, 0.18);
  border-radius: var(--r-pill);
  color: var(--accent);
  background: rgba(239, 98, 98, 0.07);
  font-size: 0.46rem;
}

.session-copy p,
.session-copy small {
  overflow: hidden;
  color: var(--muted);
  font-size: 0.55rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-copy small {
  font-size: 0.5rem;
}

.session-revoke {
  flex: none;
  color: #d99393;
}

.retry-button {
  margin-left: auto;
  flex: none;
}

.account-footnote {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  margin-top: 1.2rem;
  color: var(--muted);
  font-size: 0.54rem;
  line-height: 1.6;
  text-align: center;
}

@media (max-width: 920px) {
  .account-overview {
    grid-template-columns: 1fr;
  }

  .overview-copy {
    min-height: auto;
  }

  .overview-profile {
    min-height: 230px;
  }

  .account-layout {
    grid-template-columns: 1fr;
  }

  .account-side {
    grid-template-columns: minmax(0, 1fr) minmax(250px, 0.8fr);
  }
}

@media (max-width: 620px) {
  .topbar-inner {
    width: min(100% - 1.2rem, 1180px);
    min-height: 64px;
  }

  .account-mode,
  .session-state {
    display: none;
  }

  .topbar-actions {
    margin-left: auto;
  }

  .account-main {
    width: min(100% - 1.2rem, 1180px);
    padding-top: 1rem;
  }

  .overview-copy {
    padding: 1.25rem;
    border-radius: 20px;
  }

  .overview-copy h1 {
    font-size: 1.9rem;
  }

  .overview-profile {
    min-height: 210px;
    border-radius: 20px;
  }

  .overview-art {
    object-position: 50% 16%;
  }

  .password-grid,
  .account-side {
    grid-template-columns: 1fr;
  }

  .panel {
    border-radius: 18px;
  }

  .form-actions,
  .sessions-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .form-actions .btn,
  .sessions-heading > .btn {
    width: 100%;
  }

  .session-row {
    align-items: flex-start;
  }

  .session-copy p,
  .session-copy small {
    white-space: normal;
  }
}
</style>
