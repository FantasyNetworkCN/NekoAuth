<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { ApiError, api } from '@/lib/api'
import { formatDate } from '@/lib/format'
import { showToast } from '@/lib/toast'
import type { AdminSettings, MailSecurity, SettingsUpdate } from '@/types'

interface SettingsForm {
  serviceName: string
  registrationEnabled: boolean
  emailDomains: string
  smtp: {
    enabled: boolean
    host: string
    port: number
    username: string
    password: string
    clearPassword: boolean
    security: MailSecurity
    authEnabled: boolean
    fromEmail: string
    fromName: string
    connectionTimeoutMs: number
    readTimeoutMs: number
    writeTimeoutMs: number
  }
}

const form = reactive<SettingsForm>({
  serviceName: 'NekoAuth',
  registrationEnabled: true,
  emailDomains: '',
  smtp: {
    enabled: false,
    host: '',
    port: 587,
    username: '',
    password: '',
    clearPassword: false,
    security: 'STARTTLS',
    authEnabled: true,
    fromEmail: '',
    fromName: 'NekoAuth',
    connectionTimeoutMs: 10000,
    readTimeoutMs: 10000,
    writeTimeoutMs: 10000,
  },
})

const loading = ref(true)
const loaded = ref(false)
const saving = ref(false)
const testingMail = ref(false)
const error = ref('')
const updatedAt = ref<string | null>(null)
const passwordConfigured = ref(false)
const showPassword = ref(false)
const testRecipient = ref('')

const domains = computed(() => parseDomains(form.emailDomains))
const settingsReady = computed(() => loaded.value && !loading.value)

function parseDomains(value: string) {
  const seen = new Set<string>()
  const values: string[] = []
  for (const part of value.split(/[\s,;]+/)) {
    let domain = part.trim().toLowerCase()
    if (domain.startsWith('@')) domain = domain.slice(1)
    if (domain && !seen.has(domain)) {
      seen.add(domain)
      values.push(domain)
    }
  }
  return values
}

function apply(settings: AdminSettings) {
  form.serviceName = settings.serviceName
  form.registrationEnabled = settings.registrationEnabled
  form.emailDomains = settings.emailDomainWhitelist.join('\n')
  form.smtp.enabled = settings.smtp.enabled
  form.smtp.host = settings.smtp.host
  form.smtp.port = settings.smtp.port
  form.smtp.username = settings.smtp.username
  form.smtp.password = ''
  form.smtp.clearPassword = false
  form.smtp.security = settings.smtp.security
  form.smtp.authEnabled = settings.smtp.authEnabled
  form.smtp.fromEmail = settings.smtp.fromEmail
  form.smtp.fromName = settings.smtp.fromName
  form.smtp.connectionTimeoutMs = settings.smtp.connectionTimeoutMs
  form.smtp.readTimeoutMs = settings.smtp.readTimeoutMs
  form.smtp.writeTimeoutMs = settings.smtp.writeTimeoutMs
  passwordConfigured.value = settings.smtp.passwordConfigured
  updatedAt.value = settings.updatedAt
  if (!testRecipient.value) testRecipient.value = settings.smtp.fromEmail
}

async function load() {
  loading.value = true
  loaded.value = false
  error.value = ''
  try {
    apply(await api.settings())
    loaded.value = true
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : '无法加载系统设置'
  } finally {
    loading.value = false
  }
}

function validate() {
  if (!form.serviceName.trim()) return '服务名称不能为空'
  if (domains.value.length === 0) return '邮箱域名白名单至少保留一个域名'
  if (domains.value.length > 50) return '邮箱域名白名单最多支持 50 个域名'
  const invalidDomain = domains.value.find((domain) => !/^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/.test(domain))
  if (invalidDomain) return `邮箱域名格式不正确：${invalidDomain}`

  if (form.smtp.enabled) {
    if (!form.smtp.host.trim()) return '启用 SMTP 时必须填写服务器地址'
    if (!/^\S+@\S+\.\S+$/.test(form.smtp.fromEmail.trim())) return '启用 SMTP 时必须填写有效的发件地址'
    if (form.smtp.authEnabled && !form.smtp.username.trim()) return '启用 SMTP 认证时必须填写用户名'
    if (form.smtp.authEnabled && form.smtp.clearPassword) return '启用 SMTP 认证时不能清除密码'
    if (form.smtp.authEnabled && !passwordConfigured.value && !form.smtp.password.trim()) return '启用 SMTP 认证时必须填写密码'
  }
  if (form.smtp.password.trim() && form.smtp.clearPassword) return '不能同时设置和清除 SMTP 密码'
  if (form.smtp.fromEmail.trim() && !/^\S+@\S+\.\S+$/.test(form.smtp.fromEmail.trim())) return '发件地址格式不正确'
  return ''
}

function payload(): SettingsUpdate {
  return {
    serviceName: form.serviceName.trim(),
    registrationEnabled: form.registrationEnabled,
    emailDomainWhitelist: domains.value,
    smtp: {
      enabled: form.smtp.enabled,
      host: form.smtp.host.trim(),
      port: form.smtp.port,
      username: form.smtp.username.trim(),
      password: form.smtp.password.trim() || undefined,
      clearPassword: form.smtp.clearPassword,
      security: form.smtp.security,
      authEnabled: form.smtp.authEnabled,
      fromEmail: form.smtp.fromEmail.trim(),
      fromName: form.smtp.fromName.trim(),
      connectionTimeoutMs: form.smtp.connectionTimeoutMs,
      readTimeoutMs: form.smtp.readTimeoutMs,
      writeTimeoutMs: form.smtp.writeTimeoutMs,
    },
  }
}

async function save(notify = true): Promise<boolean> {
  if (saving.value) return false
  const problem = validate()
  if (problem) {
    error.value = problem
    showToast(problem, 'error')
    return false
  }

  saving.value = true
  error.value = ''
  try {
    apply(await api.updateSettings(payload()))
    if (notify) showToast('系统设置已保存并立即生效', 'success')
    return true
  } catch (caught) {
    const message = caught instanceof ApiError ? caught.message : '保存系统设置失败'
    error.value = message
    showToast(message, 'error')
    return false
  } finally {
    saving.value = false
  }
}

async function sendTestMail() {
  if (testingMail.value) return
  const recipient = testRecipient.value.trim() || form.smtp.fromEmail.trim()
  if (!/^\S+@\S+\.\S+$/.test(recipient)) {
    showToast('请填写有效的测试收件地址', 'error')
    return
  }
  if (!(await save(false))) return

  testingMail.value = true
  try {
    const result = await api.sendTestEmail(recipient)
    showToast(result.message, 'success')
  } catch (caught) {
    showToast(caught instanceof ApiError ? caught.message : '测试邮件发送失败', 'error')
  } finally {
    testingMail.value = false
  }
}

onMounted(() => void load())
</script>

<template>
  <div class="page settings-page">
    <header class="page-heading settings-heading">
      <div>
        <p class="page-kicker"><AppIcon name="paw" :size="13" /> NEKO CONTROL PANEL</p>
        <h1>系统设置</h1>
        <p>集中维护注册策略、邮箱域名白名单与 SMTP 发件配置。保存后立即生效，无需重启服务。</p>
      </div>
      <div class="settings-heading-meta">
        <span class="settings-live"><span></span> 即时生效</span>
        <small v-if="updatedAt">上次保存 {{ formatDate(updatedAt) }}</small>
      </div>
    </header>

    <div v-if="error" class="alert alert-error" role="alert">
      <AppIcon name="alert" :size="18" />
      <span>{{ error }}</span>
      <button class="btn btn-secondary btn-small alert-action" type="button" @click="load">重新读取</button>
    </div>

    <div v-if="loading" class="admin-loading">
      <span class="spinner"></span>
      正在读取系统设置…
    </div>

    <form v-else-if="settingsReady" class="settings-layout" novalidate @submit.prevent="save()">
      <div class="settings-main">
        <section class="settings-card panel">
          <div class="settings-section-head">
            <span class="settings-section-icon pink"><AppIcon name="users" :size="19" /></span>
            <div>
              <p class="panel-kicker">REGISTRATION POLICY</p>
              <h2>注册与邮箱白名单</h2>
              <p>用户注册时只允许使用白名单中的邮箱域名，域名必须精确匹配。</p>
            </div>
          </div>

          <label class="field">
            <span class="field-label">服务名称</span>
            <span class="input-shell">
              <AppIcon name="paw" :size="17" />
              <input v-model="form.serviceName" maxlength="80" placeholder="NekoAuth" required />
            </span>
            <span class="field-hint">用于后台与测试邮件中的服务名称。</span>
          </label>

          <label class="toggle-row">
            <input v-model="form.registrationEnabled" type="checkbox" />
            <span class="toggle-track"><span></span></span>
            <span class="toggle-copy">
              <strong>开放新用户注册</strong>
              <small>{{ form.registrationEnabled ? '当前允许使用白名单邮箱创建账户' : '当前已关闭注册入口' }}</small>
            </span>
            <span class="badge" :class="form.registrationEnabled ? 'badge-accent' : 'badge-neutral'">
              {{ form.registrationEnabled ? '已开放' : '已关闭' }}
            </span>
          </label>

          <label class="field">
            <span class="field-row">
              <span class="field-label">邮箱域名白名单</span>
              <span class="field-hint">{{ domains.length }} 个域名</span>
            </span>
            <textarea
              v-model="form.emailDomains"
              class="settings-textarea"
              rows="8"
              spellcheck="false"
              placeholder="qq.com&#10;gmail.com&#10;163.com"
            ></textarea>
            <span class="field-hint">每行一个，也可使用逗号分隔。可填写 <code>@qq.com</code>，保存时会自动规范化。</span>
          </label>

          <div class="domain-preview" aria-label="当前邮箱域名">
            <span v-for="domain in domains" :key="domain">@{{ domain }}</span>
            <span v-if="domains.length === 0" class="is-empty">尚未配置域名</span>
          </div>
        </section>

        <section class="settings-card panel">
          <div class="settings-section-head">
            <span class="settings-section-icon mint"><AppIcon name="mail" :size="19" /></span>
            <div>
              <p class="panel-kicker">OUTGOING MAIL</p>
              <h2>SMTP 与发件人</h2>
              <p>配置系统邮件发送通道。密码会加密保存，接口和页面均不会回显原文。</p>
            </div>
          </div>

          <label class="toggle-row">
            <input v-model="form.smtp.enabled" type="checkbox" />
            <span class="toggle-track"><span></span></span>
            <span class="toggle-copy">
              <strong>启用 SMTP 发件</strong>
              <small>{{ form.smtp.enabled ? '设置保存后可以发送测试邮件' : '关闭时不会连接任何 SMTP 服务器' }}</small>
            </span>
            <span class="badge" :class="form.smtp.enabled ? 'badge-accent' : 'badge-neutral'">
              {{ form.smtp.enabled ? '已启用' : '未启用' }}
            </span>
          </label>

          <div class="field-grid">
            <label class="field">
              <span class="field-label">SMTP 服务器</span>
              <span class="input-shell">
                <AppIcon name="globe" :size="17" />
                <input v-model="form.smtp.host" maxlength="255" placeholder="smtp.example.com" autocomplete="off" />
              </span>
            </label>
            <label class="field">
              <span class="field-label">端口</span>
              <span class="input-shell">
                <AppIcon name="key" :size="17" />
                <input v-model.number="form.smtp.port" type="number" min="1" max="65535" inputmode="numeric" />
              </span>
            </label>
          </div>

          <div class="field-grid">
            <label class="field">
              <span class="field-label">加密方式</span>
              <select v-model="form.smtp.security" class="settings-select">
                <option value="STARTTLS">STARTTLS（常用，端口 587）</option>
                <option value="SSL">SSL / TLS（常用，端口 465）</option>
                <option value="NONE">不加密（不推荐）</option>
              </select>
            </label>
            <label class="toggle-row compact">
              <input v-model="form.smtp.authEnabled" type="checkbox" />
              <span class="toggle-track"><span></span></span>
              <span class="toggle-copy">
                <strong>需要身份认证</strong>
                <small>多数公共邮件服务必须开启</small>
              </span>
            </label>
          </div>

          <div class="field-grid">
            <label class="field">
              <span class="field-label">SMTP 用户名</span>
              <span class="input-shell">
                <AppIcon name="user" :size="17" />
                <input v-model="form.smtp.username" maxlength="255" autocomplete="username" placeholder="通常是完整邮箱地址" />
              </span>
            </label>
            <div class="field">
              <span class="field-row">
                <span class="field-label">SMTP 密码</span>
                <span v-if="passwordConfigured" class="badge badge-accent">已保存</span>
              </span>
              <span class="input-shell">
                <AppIcon name="lock" :size="17" />
                <input
                  v-model="form.smtp.password"
                  :type="showPassword ? 'text' : 'password'"
                  maxlength="1024"
                  autocomplete="new-password"
                  :placeholder="passwordConfigured ? '已保存，留空保持不变' : '输入 SMTP 密码或授权码'"
                />
                <button class="input-action" type="button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword">
                  <AppIcon :name="showPassword ? 'eye-off' : 'eye'" :size="16" />
                </button>
              </span>
              <label v-if="passwordConfigured" class="clear-password">
                <input v-model="form.smtp.clearPassword" type="checkbox" />
                清除已保存的密码
              </label>
            </div>
          </div>

          <div class="field-grid">
            <label class="field">
              <span class="field-label">发件地址</span>
              <span class="input-shell">
                <AppIcon name="mail" :size="17" />
                <input v-model="form.smtp.fromEmail" type="email" maxlength="254" placeholder="noreply@example.com" autocomplete="off" />
              </span>
            </label>
            <label class="field">
              <span class="field-label">发件人名称</span>
              <span class="input-shell">
                <AppIcon name="spark" :size="17" />
                <input v-model="form.smtp.fromName" maxlength="120" placeholder="NekoAuth" autocomplete="off" />
              </span>
            </label>
          </div>

          <details class="advanced-settings">
            <summary><AppIcon name="sliders" :size="15" /> 高级超时设置</summary>
            <div class="field-grid advanced-grid">
              <label class="field">
                <span class="field-label">连接超时 <small>毫秒</small></span>
                <span class="input-shell"><input v-model.number="form.smtp.connectionTimeoutMs" type="number" min="1000" max="120000" step="1000" /></span>
              </label>
              <label class="field">
                <span class="field-label">读取超时 <small>毫秒</small></span>
                <span class="input-shell"><input v-model.number="form.smtp.readTimeoutMs" type="number" min="1000" max="120000" step="1000" /></span>
              </label>
              <label class="field">
                <span class="field-label">写入超时 <small>毫秒</small></span>
                <span class="input-shell"><input v-model.number="form.smtp.writeTimeoutMs" type="number" min="1000" max="120000" step="1000" /></span>
              </label>
            </div>
          </details>
        </section>
      </div>

      <aside class="settings-aside">
        <section class="settings-side-card panel">
          <div class="settings-side-head">
            <span class="settings-section-icon gold"><AppIcon name="info" :size="18" /></span>
            <div>
              <p class="panel-kicker">CURRENT STATE</p>
              <h2>当前配置</h2>
            </div>
          </div>
          <dl class="settings-summary">
            <div><dt>注册</dt><dd :class="{ on: form.registrationEnabled }">{{ form.registrationEnabled ? '开放' : '关闭' }}</dd></div>
            <div><dt>邮箱域名</dt><dd>{{ domains.length }} 个</dd></div>
            <div><dt>SMTP</dt><dd :class="{ on: form.smtp.enabled }">{{ form.smtp.enabled ? '已启用' : '未启用' }}</dd></div>
            <div><dt>密码</dt><dd>{{ passwordConfigured ? '已加密保存' : '未设置' }}</dd></div>
          </dl>
        </section>

        <section class="settings-side-card panel test-mail-card">
          <div class="settings-side-head">
            <span class="settings-section-icon pink"><AppIcon name="mail" :size="18" /></span>
            <div>
              <p class="panel-kicker">DELIVERY CHECK</p>
              <h2>发送测试邮件</h2>
            </div>
          </div>
          <p>会先保存当前表单，再使用已保存的 SMTP 配置发送。请确认服务端可以访问目标邮件服务器。</p>
          <label class="field">
            <span class="field-label">测试收件地址</span>
            <span class="input-shell">
              <AppIcon name="mail" :size="16" />
              <input v-model="testRecipient" type="email" maxlength="254" placeholder="you@qq.com" />
            </span>
          </label>
          <button class="btn btn-success btn-block" type="button" :disabled="testingMail || saving" @click="sendTestMail">
            <span v-if="testingMail" class="spinner"></span>
            <template v-else><AppIcon name="arrow-right" :size="16" /> 保存并发送测试</template>
          </button>
        </section>

        <section class="settings-tip">
          <AppIcon name="shield-cat" :size="18" />
          <div>
            <strong>敏感信息不会回显</strong>
            <p>SMTP 密码使用服务端密钥加密后写入数据库；设置接口只返回“是否已保存”。</p>
          </div>
        </section>
      </aside>

      <div class="settings-savebar">
        <span><AppIcon name="check" :size="15" /> 保存后会立即影响新的注册与发信请求</span>
        <button class="btn btn-primary" type="submit" :disabled="saving">
          <span v-if="saving" class="spinner"></span>
          <template v-else><AppIcon name="check" :size="16" /> 保存全部设置</template>
        </button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.settings-page { max-width: 1240px; }
.settings-heading { align-items: flex-start; }
.settings-heading-meta { display: grid; justify-items: end; gap: 0.45rem; flex: none; }
.settings-heading-meta small { color: #766487; font-size: 0.57rem; }
.settings-live { display: inline-flex; align-items: center; gap: 0.38rem; padding: 0.35rem 0.6rem; border: 1px solid rgba(115, 239, 219, 0.13); border-radius: 999px; color: #93eee1; background: rgba(77, 222, 204, 0.05); font-size: 0.6rem; font-weight: 650; }
.settings-live span { width: 5px; height: 5px; border-radius: 50%; background: #7ef0dc; box-shadow: 0 0 8px #7ef0dc; }

.settings-layout { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 0.85rem; margin-top: 1.5rem; align-items: start; }
.settings-main { display: grid; gap: 0.85rem; min-width: 0; }
.settings-card { display: grid; gap: 1rem; padding: 1.15rem; overflow: hidden; }
.settings-card.panel { background: rgba(27, 18, 45, 0.7); }

.settings-section-head { display: flex; align-items: flex-start; gap: 0.7rem; padding-bottom: 0.9rem; border-bottom: 1px solid rgba(255, 211, 242, 0.07); }
.settings-section-head h2 { color: #f7eaf8; font-size: 0.96rem; font-weight: 690; letter-spacing: -0.025em; }
.settings-section-head > div > p:last-child { max-width: 650px; margin-top: 0.25rem; color: #7e6a8f; font-size: 0.62rem; line-height: 1.6; }
.settings-section-icon { display: grid; width: 38px; height: 38px; flex: none; place-items: center; border-radius: 12px; }
.settings-section-icon.pink { color: #ffb1dc; background: rgba(255, 153, 215, 0.1); }
.settings-section-icon.mint { color: #93f0e1; background: rgba(91, 228, 210, 0.08); }
.settings-section-icon.gold { color: #ffd47c; background: rgba(255, 208, 112, 0.08); }

.settings-textarea,
.settings-select {
  width: 100%;
  border: 1px solid rgba(255, 205, 240, 0.11);
  border-radius: 10px;
  outline: 0;
  color: var(--text);
  background: rgba(8, 4, 17, 0.48);
  font: inherit;
  font-size: 0.76rem;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, background 0.16s ease;
}

.settings-textarea { min-height: 148px; padding: 0.7rem 0.8rem; resize: vertical; line-height: 1.7; }
.settings-select { min-height: 45px; padding: 0 0.75rem; cursor: pointer; }
.settings-textarea:focus,
.settings-select:focus { border-color: rgba(255, 161, 217, 0.5); background: rgba(10, 5, 20, 0.7); box-shadow: 0 0 0 3px rgba(249, 140, 205, 0.075); }
.settings-textarea::placeholder { color: #5c4b69; }

.toggle-row { display: flex; align-items: center; gap: 0.65rem; padding: 0.7rem 0.75rem; border: 1px solid rgba(255, 205, 240, 0.09); border-radius: 12px; background: rgba(255, 232, 248, 0.025); cursor: pointer; }
.toggle-row.compact { min-height: 69px; }
.toggle-row > input { position: absolute; width: 1px; height: 1px; opacity: 0; pointer-events: none; }
.toggle-track { position: relative; width: 35px; height: 20px; flex: none; border-radius: 999px; background: rgba(255, 255, 255, 0.1); transition: background 0.16s ease; }
.toggle-track > span { position: absolute; top: 3px; left: 3px; width: 14px; height: 14px; border-radius: 50%; background: #9884a7; transition: transform 0.16s ease, background 0.16s ease; }
.toggle-row > input:checked + .toggle-track { background: rgba(240, 139, 201, 0.42); }
.toggle-row > input:checked + .toggle-track > span { background: #fff3fb; transform: translateX(15px); }
.toggle-row > input:focus-visible + .toggle-track { outline: 2px solid rgba(255, 158, 217, 0.72); outline-offset: 2px; }
.toggle-copy { display: grid; min-width: 0; flex: 1; gap: 0.1rem; }
.toggle-copy strong { color: #d9c5dd; font-size: 0.69rem; font-weight: 620; }
.toggle-copy small { color: #776486; font-size: 0.57rem; line-height: 1.45; }

.domain-preview { display: flex; flex-wrap: wrap; gap: 0.35rem; padding-top: 0.15rem; }
.domain-preview span { padding: 0.22rem 0.48rem; border: 1px solid rgba(255, 185, 229, 0.1); border-radius: 999px; color: #c59cba; background: rgba(255, 184, 229, 0.035); font-size: 0.57rem; }
.domain-preview .is-empty { border-style: dashed; color: #71607e; }
.field-hint code { padding: 0.06rem 0.22rem; border-radius: 4px; color: #bfaabe; background: rgba(255, 255, 255, 0.045); font-size: 0.95em; }

.clear-password { display: flex; align-items: center; gap: 0.35rem; color: #a48295; font-size: 0.59rem; cursor: pointer; }
.clear-password input { accent-color: #e98bc8; }

.advanced-settings { border-top: 1px solid rgba(255, 211, 242, 0.07); padding-top: 0.8rem; }
.advanced-settings summary { display: flex; width: fit-content; align-items: center; gap: 0.35rem; color: #8d7699; font-size: 0.63rem; cursor: pointer; user-select: none; }
.advanced-settings summary:hover { color: #c8aec9; }
.advanced-grid { margin-top: 0.75rem; grid-template-columns: repeat(3, minmax(0, 1fr)); }

.settings-aside { display: grid; gap: 0.75rem; position: sticky; top: 1rem; }
.settings-side-card { padding: 1rem; }
.settings-side-head { display: flex; align-items: center; gap: 0.55rem; margin-bottom: 0.8rem; }
.settings-side-head h2 { color: #f1e1f2; font-size: 0.78rem; font-weight: 660; }
.settings-side-head .panel-kicker { margin-bottom: 0.2rem; font-size: 0.49rem; }
.settings-summary { display: grid; gap: 0.5rem; }
.settings-summary > div { display: flex; align-items: center; justify-content: space-between; gap: 0.7rem; padding-bottom: 0.5rem; border-bottom: 1px solid rgba(255, 255, 255, 0.045); }
.settings-summary > div:last-child { padding-bottom: 0; border-bottom: 0; }
.settings-summary dt { color: #756282; font-size: 0.59rem; }
.settings-summary dd { color: #c9b5cc; font-size: 0.62rem; font-weight: 620; }
.settings-summary dd.on { color: #8cecdf; }

.test-mail-card { display: grid; gap: 0.75rem; }
.test-mail-card > p { color: #7c688a; font-size: 0.59rem; line-height: 1.65; }
.test-mail-card .settings-side-head { margin-bottom: 0; }

.settings-tip { display: flex; align-items: flex-start; gap: 0.55rem; padding: 0.8rem 0.85rem; border: 1px solid rgba(103, 235, 217, 0.09); border-radius: 13px; color: #7ec4bb; background: rgba(76, 218, 200, 0.035); }
.settings-tip > svg { flex: none; margin-top: 0.05rem; }
.settings-tip strong { display: block; color: #9ad8d1; font-size: 0.62rem; font-weight: 640; }
.settings-tip p { margin-top: 0.18rem; color: #6c9c97; font-size: 0.55rem; line-height: 1.6; }

.settings-savebar { grid-column: 1 / -1; position: sticky; z-index: 10; bottom: 0.75rem; display: flex; align-items: center; justify-content: space-between; gap: 1rem; padding: 0.75rem 0.85rem; border: 1px solid rgba(255, 192, 232, 0.16); border-radius: 14px; background: rgba(24, 15, 40, 0.9); box-shadow: 0 16px 40px rgba(3, 0, 10, 0.32); backdrop-filter: blur(18px); }
.settings-savebar > span { display: inline-flex; align-items: center; gap: 0.4rem; color: #8a7596; font-size: 0.61rem; }

.alert-action { margin-left: auto; }

@media (max-width: 1050px) {
  .settings-layout { grid-template-columns: 1fr; }
  .settings-aside { position: static; grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .settings-tip { grid-column: 1 / -1; }
}

@media (max-width: 700px) {
  .settings-heading-meta { display: none; }
  .settings-aside { grid-template-columns: 1fr; }
  .settings-tip { grid-column: auto; }
  .advanced-grid { grid-template-columns: 1fr; }
  .settings-savebar { align-items: stretch; flex-direction: column; }
  .settings-savebar .btn { width: 100%; }
}
</style>
