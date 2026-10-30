<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import AppLogo from '@/components/AppLogo.vue'
import NekoMascot from '@/components/NekoMascot.vue'
import { ApiError, api } from '@/lib/api'
import type { SetupInstallRequest, SetupResult } from '@/types'

const setupToken = ref('')
const loading = ref(false)
const checking = ref(true)
const complete = ref(false)
const unavailable = ref(false)
const error = ref('')
const fieldErrors = ref<Record<string, string>>({})
const result = ref<SetupResult | null>(null)
const showAdminPassword = ref(false)

const form = reactive<SetupInstallRequest & { adminPasswordConfirm: string }>({
  adminUsername: 'admin',
  adminEmail: '',
  adminPassword: '',
  adminPasswordConfirm: '',
  mysqlHost: '127.0.0.1',
  mysqlPort: 3306,
  mysqlDatabase: 'nekoauth',
  mysqlUsername: 'nekoauth',
  mysqlPassword: '',
  redisHost: '127.0.0.1',
  redisPort: 6379,
  redisPassword: '',
  redisDatabase: 0,
})

onMounted(async () => {
  try {
    const status = await api.setupStatus()
    unavailable.value = !status.required
  } catch (caught) {
    if (caught instanceof ApiError && caught.status === 404) {
      complete.value = true
      return
    }
    error.value = caught instanceof ApiError ? caught.message : '无法读取安装状态，请检查服务器连接'
  } finally {
    checking.value = false
  }
})

function fieldError(name: string) {
  return fieldErrors.value[name] || ''
}

async function submit() {
  if (loading.value) return
  error.value = ''
  fieldErrors.value = {}

  if (form.adminPassword !== form.adminPasswordConfirm) {
    fieldErrors.value = { adminPassword: '两次输入的管理员密码不一致' }
    return
  }
  if (!setupToken.value.trim()) {
    fieldErrors.value = { setupToken: '请输入服务启动日志中的一次性安装令牌' }
    return
  }

  loading.value = true
  try {
    const payload: SetupInstallRequest = {
      adminUsername: form.adminUsername,
      adminEmail: form.adminEmail,
      adminPassword: form.adminPassword,
      mysqlHost: form.mysqlHost,
      mysqlPort: form.mysqlPort,
      mysqlDatabase: form.mysqlDatabase,
      mysqlUsername: form.mysqlUsername,
      mysqlPassword: form.mysqlPassword,
      redisHost: form.redisHost,
      redisPort: form.redisPort,
      redisPassword: form.redisPassword,
      redisDatabase: form.redisDatabase,
    }
    result.value = await api.installSetup(payload, setupToken.value.trim())
  } catch (caught) {
    if (caught instanceof ApiError) {
      error.value = caught.message
      fieldErrors.value = caught.fields
      if (caught.status === 404) complete.value = true
    } else {
      error.value = '网络异常，请稍后重试'
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="setup-page">
    <aside class="setup-aside">
      <AppLogo :size="43" />
      <div class="aside-copy">
        <p class="setup-kicker"><AppIcon name="spark" :size="15" /> NEKO FIRST BOOT</p>
        <h1>一次契约，<br /><span>守护整片梦域。</span></h1>
        <p>猫娘向导小梦会陪你完成首次安装。管理员、MySQL/MariaDB 和 Redis 配置写入本机数据目录后，<code>/setup</code> 会永久收起猫爪印。</p>
      </div>

      <div class="aside-neko">
        <NekoMascot pose="wink" compact />
        <div><strong>小梦安装向导</strong><span>跟着猫爪印走，不会迷路喵。</span></div>
      </div>

      <ol class="setup-steps">
        <li class="active"><span>01</span><div><strong>管理员账户</strong><small>创建首个 ADMIN</small></div></li>
        <li><span>02</span><div><strong>连接存储</strong><small>校验数据库与 Redis</small></div></li>
        <li><span>03</span><div><strong>重启生效</strong><small>切换生产数据库</small></div></li>
      </ol>

      <div class="aside-note">
        <AppIcon name="shield-cat" :size="17" />
        <span>配置与安装标记仅当前系统用户可读，不会写入 Git。</span>
      </div>
    </aside>

    <main class="setup-main">
      <div class="setup-shell">
        <div v-if="checking" class="state-card">
          <span class="spinner"></span>
          <p>小梦正在检查安装状态…</p>
        </div>

        <div v-else-if="complete" class="state-card state-complete">
          <span class="state-icon"><AppIcon name="check" :size="25" /></span>
          <p class="setup-kicker">Setup closed · 猫爪封印</p>
          <h2>安装入口已永久关闭</h2>
          <p>当前实例已经完成安装，<code>/setup</code> 与 <code>/api/setup/**</code> 均返回 404。请直接访问登录页。</p>
          <a class="btn btn-primary" href="/login">前往登录</a>
        </div>

        <div v-else-if="result" class="state-card state-complete">
          <span class="state-icon"><AppIcon name="check" :size="25" /></span>
          <p class="setup-kicker">Configuration saved · 契约成立</p>
          <h2>配置已安全保存</h2>
          <p>{{ result.message }}</p>
          <div class="restart-command"><code>docker compose restart auth</code></div>
          <p class="state-hint">重启后管理员账户会自动创建，服务将切换到 MySQL/MariaDB 和 Redis。</p>
        </div>

        <template v-else>
          <header class="setup-heading">
            <div>
              <p class="setup-kicker">FantasyNetwork installer</p>
              <h2>唤醒梦幻网络的猫娘认证</h2>
              <p>填写一次性安装令牌与基础设施连接信息，小梦会在提交前亲自检查数据库和 Redis。</p>
            </div>
            <span class="setup-badge"><span></span> 仅首次可访问</span>
          </header>

          <div v-if="unavailable" class="alert alert-error" role="alert">
            <AppIcon name="alert" :size="18" />
            <span>安装入口当前不可用，请检查服务日志。</span>
          </div>
          <div v-if="error" class="alert alert-error" role="alert">
            <AppIcon name="alert" :size="18" />
            <span>{{ error }}</span>
          </div>

          <form class="setup-form" @submit.prevent="submit">
            <section class="form-section token-section">
              <div class="section-title">
                <span class="section-number">00</span>
                <div><h3>一次性安装令牌</h3><p>从服务启动日志复制，只对本次安装进程有效。</p></div>
              </div>
              <label class="setup-field">
                <span class="setup-label">Setup token</span>
                <input
                  v-model="setupToken"
                  class="setup-input mono"
                  :class="{ invalid: fieldError('setupToken') }"
                  type="password"
                  autocomplete="off"
                  placeholder="启动日志中的一次性令牌"
                />
                <span v-if="fieldError('setupToken')" class="setup-error">{{ fieldError('setupToken') }}</span>
              </label>
              <p class="token-hint"><code>docker compose logs auth</code> <span>→</span> 查找“一次性安装令牌”</p>
            </section>

            <section class="form-section">
              <div class="section-title">
                <span class="section-number">01</span>
                <div><h3>管理员账户</h3><p>安装完成后由服务端自动创建，角色固定为 ADMIN。</p></div>
              </div>
              <div class="field-grid">
                <label class="setup-field">
                  <span class="setup-label">用户名</span>
                  <input v-model="form.adminUsername" class="setup-input" :class="{ invalid: fieldError('adminUsername') }" autocomplete="username" maxlength="32" />
                  <span v-if="fieldError('adminUsername')" class="setup-error">{{ fieldError('adminUsername') }}</span>
                </label>
                <label class="setup-field span-two">
                  <span class="setup-label">邮箱</span>
                  <input v-model="form.adminEmail" class="setup-input" :class="{ invalid: fieldError('adminEmail') }" type="email" autocomplete="email" maxlength="254" placeholder="admin@example.com" />
                  <span v-if="fieldError('adminEmail')" class="setup-error">{{ fieldError('adminEmail') }}</span>
                </label>
                <label class="setup-field">
                  <span class="setup-label">密码</span>
                  <span class="password-wrap">
                  <input v-model="form.adminPassword" class="setup-input" :class="{ invalid: fieldError('adminPassword') }" :type="showAdminPassword ? 'text' : 'password'" autocomplete="new-password" maxlength="30" />
                    <button type="button" class="password-toggle" :aria-label="showAdminPassword ? '隐藏密码' : '显示密码'" @click="showAdminPassword = !showAdminPassword">
                      <AppIcon :name="showAdminPassword ? 'eye-off' : 'eye'" :size="17" />
                    </button>
                  </span>
                  <span v-if="fieldError('adminPassword')" class="setup-error">{{ fieldError('adminPassword') }}</span>
                </label>
                <label class="setup-field">
                  <span class="setup-label">确认密码</span>
                  <input v-model="form.adminPasswordConfirm" class="setup-input" :type="showAdminPassword ? 'text' : 'password'" autocomplete="new-password" maxlength="30" />
                </label>
              </div>
            </section>

            <section class="form-section">
              <div class="section-title">
                <span class="section-number">02</span>
                <div><h3>MySQL / MariaDB</h3><p>目标数据库需已存在；支持 MySQL 8 与 MariaDB 10.6+，启动后 Hibernate 会按实体自动创建表结构。</p></div>
              </div>
              <div class="field-grid">
                <label class="setup-field span-two">
                  <span class="setup-label">主机</span>
                  <input v-model="form.mysqlHost" class="setup-input mono" :class="{ invalid: fieldError('mysqlHost') }" placeholder="mysql" />
                  <span v-if="fieldError('mysqlHost')" class="setup-error">{{ fieldError('mysqlHost') }}</span>
                </label>
                <label class="setup-field">
                  <span class="setup-label">端口</span>
                  <input v-model.number="form.mysqlPort" class="setup-input mono" type="number" min="1" max="65535" />
                </label>
                <label class="setup-field">
                  <span class="setup-label">数据库名</span>
                  <input v-model="form.mysqlDatabase" class="setup-input mono" :class="{ invalid: fieldError('mysqlDatabase') }" maxlength="64" />
                  <span v-if="fieldError('mysqlDatabase')" class="setup-error">{{ fieldError('mysqlDatabase') }}</span>
                </label>
                <label class="setup-field">
                  <span class="setup-label">用户名</span>
                  <input v-model="form.mysqlUsername" class="setup-input mono" autocomplete="off" maxlength="128" />
                </label>
                <label class="setup-field">
                  <span class="setup-label">密码</span>
                  <input v-model="form.mysqlPassword" class="setup-input" type="password" autocomplete="new-password" maxlength="256" />
                </label>
              </div>
            </section>

            <section class="form-section">
              <div class="section-title">
                <span class="section-number">03</span>
                <div><h3>Redis</h3><p>用于跨实例共享登录失败计数，降低暴力破解风险。</p></div>
              </div>
              <div class="field-grid">
                <label class="setup-field span-two">
                  <span class="setup-label">主机</span>
                  <input v-model="form.redisHost" class="setup-input mono" :class="{ invalid: fieldError('redisHost') }" placeholder="redis" />
                  <span v-if="fieldError('redisHost')" class="setup-error">{{ fieldError('redisHost') }}</span>
                </label>
                <label class="setup-field">
                  <span class="setup-label">端口</span>
                  <input v-model.number="form.redisPort" class="setup-input mono" type="number" min="1" max="65535" />
                </label>
                <label class="setup-field">
                  <span class="setup-label">数据库编号</span>
                  <input v-model.number="form.redisDatabase" class="setup-input mono" type="number" min="0" max="255" />
                </label>
                <label class="setup-field span-two">
                  <span class="setup-label">密码 <small>可选</small></span>
                  <input v-model="form.redisPassword" class="setup-input" type="password" autocomplete="new-password" maxlength="256" />
                </label>
              </div>
            </section>

            <div class="setup-submit-row">
              <p><AppIcon name="lock" :size="14" /> 凭据只写入服务器本地 <code>data/nekoauth-setup.yml</code></p>
              <button class="btn btn-primary setup-submit" type="submit" :disabled="loading || unavailable">
                <span v-if="loading" class="spinner"></span>
                <template v-else>校验并完成安装 <AppIcon name="arrow-right" :size="17" /></template>
              </button>
            </div>
          </form>
        </template>
      </div>
    </main>
  </div>
</template>

<style scoped>
.aside-neko {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin: -0.4rem 0 0.8rem;
  padding: 0.45rem 0.55rem 0.45rem 0.1rem;
  border: 1px solid rgba(255, 200, 237, 0.08);
  border-radius: 13px;
  background: rgba(255, 227, 248, 0.025);
}

.aside-neko :deep(.neko-mascot) { width: 70px; flex: none; }

.aside-neko > div { display: grid; gap: 0.1rem; }
.aside-neko strong { color: #e8cfe5; font-size: 0.61rem; font-weight: 700; }
.aside-neko span { color: #806b8e; font-size: 0.49rem; line-height: 1.5; }

.setup-page {
  display: grid;
  min-height: 100svh;
  grid-template-columns: minmax(300px, 0.72fr) minmax(0, 1.28fr);
  background:
    radial-gradient(circle at 80% 0%, rgba(91, 108, 242, 0.11), transparent 32%),
    var(--bg);
}

.setup-aside {
  position: sticky;
  top: 0;
  display: flex;
  height: 100svh;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
  padding: clamp(1.7rem, 4vw, 3.2rem);
  border-right: 1px solid var(--border);
  background:
    radial-gradient(circle at 100% 20%, rgba(83, 105, 255, 0.2), transparent 38%),
    radial-gradient(circle at 0% 85%, rgba(66, 232, 197, 0.07), transparent 35%),
    #090d1a;
}

.setup-aside::before {
  position: absolute;
  inset: 0;
  opacity: 0.32;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.025) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.025) 1px, transparent 1px);
  background-size: 48px 48px;
  content: '';
  mask-image: linear-gradient(to bottom, black, transparent 90%);
  pointer-events: none;
}

.setup-aside > * {
  position: relative;
  z-index: 1;
}

.aside-copy {
  margin: auto 0;
  padding: 3rem 0 2.4rem;
}

.setup-kicker {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  margin-bottom: 0.8rem;
  color: var(--primary-soft);
  font-size: 0.66rem;
  font-weight: 700;
  letter-spacing: 0.13em;
  text-transform: uppercase;
}

.aside-copy h1 {
  color: #f4f6ff;
  font-size: clamp(2.2rem, 4vw, 3.7rem);
  font-weight: 760;
  letter-spacing: -0.06em;
  line-height: 1.02;
}

.aside-copy h1 span {
  color: transparent;
  background: linear-gradient(100deg, #b9c7ff, #7f9bff 52%, #5fe7cc);
  background-clip: text;
}

.aside-copy > p:last-child {
  max-width: 410px;
  margin-top: 1.35rem;
  color: #8e9ab7;
  font-size: 0.82rem;
  line-height: 1.75;
}

.aside-copy code,
.state-card code {
  color: #a9b8df;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.92em;
}

.setup-steps {
  display: grid;
  gap: 0.55rem;
  padding: 0;
  list-style: none;
}

.setup-steps li {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  padding: 0.7rem;
  border: 1px solid rgba(255, 255, 255, 0.055);
  border-radius: 11px;
  color: #65708b;
  background: rgba(255, 255, 255, 0.018);
}

.setup-steps li.active {
  border-color: rgba(124, 157, 255, 0.18);
  color: #c6d1f7;
  background: rgba(100, 126, 255, 0.065);
}

.setup-steps li > span {
  display: grid;
  width: 29px;
  height: 29px;
  flex: none;
  place-items: center;
  border-radius: 8px;
  color: #8190b3;
  background: rgba(255, 255, 255, 0.045);
  font-family: ui-monospace, monospace;
  font-size: 0.61rem;
}

.setup-steps .active > span {
  color: #dfe5ff;
  background: rgba(111, 138, 255, 0.17);
}

.setup-steps strong,
.setup-steps small {
  display: block;
}

.setup-steps strong {
  font-size: 0.7rem;
  font-weight: 620;
}

.setup-steps small {
  margin-top: 0.1rem;
  color: #69758f;
  font-size: 0.61rem;
}

.aside-note {
  display: flex;
  align-items: flex-start;
  gap: 0.55rem;
  padding-top: 1.1rem;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  color: #65718d;
  font-size: 0.64rem;
  line-height: 1.55;
}

.aside-note svg {
  flex: none;
  color: #88a0ff;
}

.setup-main {
  min-width: 0;
  padding: clamp(1.2rem, 4vw, 3.8rem);
}

.setup-shell {
  width: min(880px, 100%);
  margin: 0 auto;
  animation: setup-in 0.45s ease-out both;
}

.setup-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1.5rem;
  margin: 1rem 0 1.8rem;
}

.setup-heading h2,
.state-card h2 {
  color: #f0f3ff;
  font-size: clamp(1.9rem, 3.6vw, 2.7rem);
  font-weight: 750;
  letter-spacing: -0.055em;
  line-height: 1.06;
}

.setup-heading p:not(.setup-kicker) {
  max-width: 570px;
  margin-top: 0.7rem;
  color: var(--muted);
  font-size: 0.77rem;
  line-height: 1.7;
}

.setup-badge {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 0.4rem;
  margin-top: 0.5rem;
  padding: 0.4rem 0.64rem;
  border: 1px solid rgba(66, 232, 197, 0.14);
  border-radius: 999px;
  color: #8ae8d5;
  background: rgba(66, 232, 197, 0.05);
  font-size: 0.6rem;
}

.setup-badge span {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 8px var(--accent);
}

.setup-form {
  display: grid;
  gap: 0.9rem;
}

.form-section {
  padding: 1.2rem;
  border: 1px solid var(--border);
  border-radius: 16px;
  background: rgba(16, 22, 41, 0.64);
  box-shadow: 0 18px 44px rgba(0, 0, 0, 0.13);
  backdrop-filter: blur(12px);
}

.token-section {
  border-color: rgba(124, 157, 255, 0.18);
  background:
    linear-gradient(110deg, rgba(95, 119, 245, 0.075), transparent 60%),
    rgba(16, 22, 41, 0.72);
}

.section-title {
  display: flex;
  align-items: flex-start;
  gap: 0.7rem;
  margin-bottom: 1rem;
}

.section-number {
  display: grid;
  width: 28px;
  height: 28px;
  flex: none;
  place-items: center;
  border: 1px solid rgba(132, 157, 255, 0.15);
  border-radius: 8px;
  color: #a9baff;
  background: rgba(109, 133, 255, 0.07);
  font-family: ui-monospace, monospace;
  font-size: 0.59rem;
}

.section-title h3 {
  color: #dfe5f8;
  font-size: 0.83rem;
  font-weight: 650;
}

.section-title p {
  margin-top: 0.22rem;
  color: #6f7b96;
  font-size: 0.64rem;
  line-height: 1.55;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.75rem;
}

.span-two {
  grid-column: span 2;
}

.setup-field {
  display: grid;
  gap: 0.34rem;
}

.setup-label {
  color: #aab5cd;
  font-size: 0.65rem;
  font-weight: 570;
}

.setup-label small {
  color: #606b84;
  font-size: 0.56rem;
}

.setup-input {
  width: 100%;
  min-height: 41px;
  padding: 0.55rem 0.7rem;
  border: 1px solid rgba(255, 255, 255, 0.085);
  border-radius: 9px;
  outline: none;
  color: var(--text);
  background: rgba(6, 10, 21, 0.52);
  font-size: 0.73rem;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, background 0.16s ease;
}

.setup-input.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.69rem;
}

.setup-input:focus {
  border-color: rgba(124, 157, 255, 0.48);
  background: rgba(7, 11, 24, 0.78);
  box-shadow: 0 0 0 3px rgba(101, 127, 255, 0.08);
}

.setup-input.invalid {
  border-color: rgba(255, 107, 129, 0.47);
}

.setup-input::placeholder {
  color: #4d5871;
}

.password-wrap {
  position: relative;
  display: block;
}

.password-wrap .setup-input {
  padding-right: 2.5rem;
}

.password-toggle {
  position: absolute;
  top: 50%;
  right: 0.4rem;
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border: 0;
  border-radius: 7px;
  color: #69758f;
  background: transparent;
  transform: translateY(-50%);
  cursor: pointer;
}

.password-toggle:hover {
  color: #b8c4de;
  background: rgba(255, 255, 255, 0.04);
}

.setup-error {
  color: #ff8e9f;
  font-size: 0.58rem;
}

.token-hint {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-top: 0.6rem;
  color: #66728d;
  font-size: 0.6rem;
}

.token-hint code,
.setup-submit-row code {
  color: #a9b8df;
  font-family: ui-monospace, monospace;
}

.token-hint span {
  color: #4d5871;
}

.setup-submit-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.8rem 0 0.3rem;
}

.setup-submit-row > p {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  color: #66728c;
  font-size: 0.61rem;
  line-height: 1.5;
}

.setup-submit-row > p svg {
  flex: none;
}

.setup-submit {
  min-height: 44px;
  padding-inline: 1.15rem;
}

.state-card {
  display: grid;
  width: min(530px, 100%);
  justify-items: center;
  margin: 18vh auto 0;
  padding: 2.4rem;
  border: 1px solid var(--border);
  border-radius: 18px;
  background: var(--surface);
  box-shadow: var(--shadow);
  text-align: center;
  backdrop-filter: blur(14px);
}

.state-card > .spinner {
  margin-bottom: 0.8rem;
}

.state-card p:not(.setup-kicker) {
  max-width: 430px;
  margin-top: 0.85rem;
  color: var(--muted);
  font-size: 0.75rem;
  line-height: 1.7;
}

.state-card .setup-kicker {
  justify-content: center;
  margin-top: 1rem;
}

.state-icon {
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  border: 1px solid rgba(66, 232, 197, 0.2);
  border-radius: 14px;
  color: var(--accent);
  background: rgba(66, 232, 197, 0.07);
}

.state-card .btn {
  margin-top: 1.5rem;
}

.restart-command {
  margin-top: 1.2rem;
  padding: 0.65rem 0.9rem;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 9px;
  color: #bec9e2;
  background: rgba(5, 8, 18, 0.6);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.7rem;
}

.state-card .state-hint {
  color: #656f88;
  font-size: 0.64rem;
}

@keyframes setup-in {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (max-width: 920px) {
  .setup-page {
    grid-template-columns: 1fr;
  }

  .setup-aside {
    position: relative;
    height: auto;
    gap: 1.4rem;
    padding: 1.5rem 1.2rem;
    border-right: 0;
    border-bottom: 1px solid var(--border);
  }

  .aside-copy {
    padding: 1.3rem 0 0.3rem;
  }

  .aside-copy h1 {
    font-size: 2.3rem;
  }

  .setup-steps,
  .aside-note {
    display: none;
  }

  .setup-main {
    padding: 1.3rem;
  }
}

@media (max-width: 620px) {
  .field-grid {
    grid-template-columns: 1fr;
  }

  .span-two {
    grid-column: auto;
  }

  .setup-heading,
  .setup-submit-row {
    align-items: stretch;
    flex-direction: column;
  }

  .setup-badge {
    width: fit-content;
  }

  .setup-submit {
    width: 100%;
  }

  .form-section {
    padding: 1rem;
  }
}
</style>
