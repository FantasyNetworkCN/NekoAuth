<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import NekoMascot from '@/components/NekoMascot.vue'
import { ApiError, api } from '@/lib/api'
import { formatDate } from '@/lib/format'
import { showToast } from '@/lib/toast'
import type { CreatedOAuthClient, OAuthClient } from '@/types'

const clients = ref<OAuthClient[]>([])
const loading = ref(true)
const creating = ref(false)
const showCreate = ref(false)
const busyId = ref<number | null>(null)
const error = ref('')
const secretReveal = ref<CreatedOAuthClient | null>(null)
const copied = ref('')

const allScopes = [
  { name: 'openid', label: 'OpenID', description: '签发 OIDC 身份' },
  { name: 'profile', label: '资料', description: '昵称与用户名' },
  { name: 'email', label: '邮箱', description: '邮箱与验证状态' },
  { name: 'roles', label: '阵营', description: '用户或管理员角色' },
  { name: 'offline_access', label: '保持登录', description: '签发刷新令牌' },
]
const form = reactive({
  name: '',
  description: '',
  redirectUris: '',
  scopes: ['openid', 'profile', 'email'] as string[],
  publicClient: true,
})

const enabledCount = computed(() => clients.value.filter((client) => client.enabled).length)
const confidentialCount = computed(() => clients.value.filter((client) => !client.publicClient).length)

async function load() {
  loading.value = true
  error.value = ''
  try {
    clients.value = await api.oauthClients()
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : '无法加载 OAuth 应用'
  } finally {
    loading.value = false
  }
}

function toggleScope(name: string) {
  if (form.scopes.includes(name)) {
    if (form.scopes.length === 1) return
    form.scopes = form.scopes.filter((scope) => scope !== name)
  } else {
    form.scopes = [...form.scopes, name]
  }
}

async function createClient() {
  if (creating.value) return
  const redirectUris = form.redirectUris
    .split(/\n+/)
    .map((value) => value.trim())
    .filter(Boolean)
  if (!form.name.trim() || redirectUris.length === 0 || form.scopes.length === 0) {
    showToast('请填写应用名、至少一个回调地址和一个权限范围', 'error')
    return
  }
  creating.value = true
  try {
    const result = await api.createOAuthClient({
      name: form.name.trim(),
      description: form.description.trim(),
      redirectUris,
      scopes: form.scopes,
      publicClient: form.publicClient,
    })
    clients.value = [result.client, ...clients.value]
    showCreate.value = false
    form.name = ''
    form.description = ''
    form.redirectUris = ''
    form.scopes = ['openid', 'profile', 'email']
    form.publicClient = true
    if (result.clientSecret) {
      secretReveal.value = result
    } else {
      showToast('OAuth 应用已创建，公共客户端无需密钥', 'success')
    }
  } catch (caught) {
    showToast(caught instanceof ApiError ? caught.message : '创建应用失败', 'error')
  } finally {
    creating.value = false
  }
}

async function toggleEnabled(client: OAuthClient) {
  if (busyId.value !== null) return
  busyId.value = client.id
  try {
    const updated = await api.updateOAuthClient(client.id, { enabled: !client.enabled })
    replaceClient(updated)
    showToast(`${updated.name} 已${updated.enabled ? '启用' : '停用'}`, 'success')
  } catch (caught) {
    showToast(caught instanceof ApiError ? caught.message : '更新应用失败', 'error')
  } finally {
    busyId.value = null
  }
}

async function rotateSecret(client: OAuthClient) {
  if (busyId.value !== null || client.publicClient) return
  busyId.value = client.id
  try {
    const result = await api.rotateOAuthClientSecret(client.id)
    replaceClient(result.client)
    secretReveal.value = result
  } catch (caught) {
    showToast(caught instanceof ApiError ? caught.message : '轮换密钥失败', 'error')
  } finally {
    busyId.value = null
  }
}

function replaceClient(updated: OAuthClient) {
  const index = clients.value.findIndex((client) => client.id === updated.id)
  if (index >= 0) clients.value[index] = updated
}

async function copy(value: string, label: string) {
  try {
    await navigator.clipboard.writeText(value)
    copied.value = label
    showToast(`${label}已复制`, 'success')
    window.setTimeout(() => { copied.value = '' }, 1800)
  } catch {
    showToast('浏览器未允许访问剪贴板', 'error')
  }
}

onMounted(() => void load())
</script>

<template>
  <div class="page clients-page">
    <header class="page-heading clients-heading">
      <div>
        <p class="page-kicker"><AppIcon name="paw" :size="13" /> NEKO APP ATELIER</p>
        <h1>OAuth 应用猫屋</h1>
        <p>登记可以接入梦幻网络的应用，管理回调地址、权限范围与客户端密钥。每只应用都要先拿到猫爪许可。</p>
      </div>
      <button class="btn btn-primary" type="button" @click="showCreate = !showCreate">
        <AppIcon :name="showCreate ? 'close' : 'spark'" :size="17" />
        {{ showCreate ? '收起卷轴' : '登记新应用' }}
      </button>
    </header>

    <section class="client-overview">
      <div class="overview-card">
        <span class="overview-icon pink"><AppIcon name="apps" :size="19" /></span>
        <span><strong>{{ clients.length }}</strong><small>已登记应用</small></span>
      </div>
      <div class="overview-card">
        <span class="overview-icon mint"><AppIcon name="shield-cat" :size="19" /></span>
        <span><strong>{{ enabledCount }}</strong><small>正在营业</small></span>
      </div>
      <div class="overview-card">
        <span class="overview-icon gold"><AppIcon name="key" :size="19" /></span>
        <span><strong>{{ confidentialCount }}</strong><small>机密客户端</small></span>
      </div>
      <div class="overview-neko">
        <NekoMascot pose="wink" compact />
        <span>小梦会盯紧每一条 redirect_uri 喵。</span>
      </div>
    </section>

    <Transition name="atelier">
      <section v-if="showCreate" class="create-panel">
        <div class="create-copy">
          <p class="page-kicker">NEW CLIENT SCROLL</p>
          <h2>写下新应用的契约</h2>
          <p>浏览器单页应用请选择“公共客户端”并启用 PKCE；服务端应用可以使用客户端密钥。</p>
          <div class="create-tip"><AppIcon name="info" :size="15" /><span>回调地址必须与授权请求完全一致，不允许通配符。</span></div>
        </div>
        <form class="create-form" @submit.prevent="createClient">
          <div class="field-grid">
            <label class="field">
              <span class="field-label">应用名称</span>
              <span class="input-shell"><AppIcon name="apps" :size="17" /><input v-model="form.name" maxlength="80" placeholder="星海图鉴" required /></span>
            </label>
            <label class="field">
              <span class="field-label">应用简介 <small>可选</small></span>
              <span class="input-shell"><AppIcon name="spark" :size="17" /><input v-model="form.description" maxlength="500" placeholder="在幻想世界收集星星的应用" /></span>
            </label>
          </div>
          <label class="field">
            <span class="field-label">回调地址 <small>每行一个</small></span>
            <textarea v-model="form.redirectUris" class="atelier-textarea" rows="3" placeholder="https://app.fantasy.network/oauth/callback" required></textarea>
          </label>
          <div class="field">
            <span class="field-label">申请权限</span>
            <div class="scope-pills">
              <button
                v-for="scope in allScopes"
                :key="scope.name"
                class="scope-choice"
                :class="{ active: form.scopes.includes(scope.name) }"
                type="button"
                @click="toggleScope(scope.name)"
              >
                <AppIcon :name="form.scopes.includes(scope.name) ? 'check' : 'plus'" :size="12" />
                <span><strong>{{ scope.label }}</strong><small>{{ scope.description }}</small></span>
              </button>
            </div>
          </div>
          <div class="client-mode-row">
            <label class="mode-switch">
              <input v-model="form.publicClient" type="checkbox" />
              <span class="switch-track"><i></i></span>
              <span><strong>公共客户端</strong><small>强制 PKCE S256，不保存客户端密钥</small></span>
            </label>
            <button class="btn btn-primary" type="submit" :disabled="creating">
              <span v-if="creating" class="spinner"></span>
              <template v-else><AppIcon name="paw" :size="16" /> 烙下猫爪印</template>
            </button>
          </div>
        </form>
      </section>
    </Transition>

    <div v-if="error" class="alert alert-error" role="alert">
      <AppIcon name="alert" :size="18" /> {{ error }}
      <button class="alert-action" type="button" @click="load">重试</button>
    </div>

    <div v-if="loading" class="admin-loading">
      <span class="spinner"></span> 小梦正在翻应用名册…
    </div>

    <div v-else-if="clients.length === 0" class="empty-state panel clients-empty">
      <NekoMascot pose="sleep" compact />
      <h2>猫屋里还没有应用</h2>
      <p>登记第一个 OAuth 应用，让它也能看见梦幻网络的星光。</p>
      <button class="btn btn-secondary" type="button" @click="showCreate = true">登记第一个应用</button>
    </div>

    <section v-else class="client-grid" aria-label="OAuth 应用列表">
      <article v-for="client in clients" :key="client.id" class="client-card" :class="{ 'is-disabled': !client.enabled }">
        <div class="client-card-head">
          <span class="client-avatar" :class="{ confidential: !client.publicClient }">{{ client.name.slice(0, 1).toUpperCase() }}</span>
          <div class="client-title">
            <div><h2>{{ client.name }}</h2><span class="client-type">{{ client.publicClient ? 'PUBLIC' : 'CONFIDENTIAL' }}</span></div>
            <p>{{ client.description || '来自梦幻网络的 OAuth 应用' }}</p>
          </div>
          <span class="status-pill" :class="client.enabled ? 'is-online' : 'is-offline'"><span></span>{{ client.enabled ? '启用' : '停用' }}</span>
        </div>

        <div class="client-id-row">
          <span><small>CLIENT ID</small><code>{{ client.clientId }}</code></span>
          <button class="icon-button" type="button" aria-label="复制 Client ID" @click="copy(client.clientId, 'Client ID')">
            <AppIcon name="copy" :size="15" />
          </button>
        </div>

        <div class="client-uri-list">
          <span><AppIcon name="globe" :size="13" /> 回调地址</span>
          <code v-for="uri in client.redirectUris.slice(0, 2)" :key="uri">{{ uri }}</code>
          <small v-if="client.redirectUris.length > 2">还有 {{ client.redirectUris.length - 2 }} 个地址</small>
        </div>

        <div class="client-scopes">
          <span v-for="scope in client.scopes" :key="scope" class="scope-tag">{{ scope }}</span>
        </div>

        <div class="client-card-foot">
          <span class="client-date"><AppIcon name="clock" :size="13" /> {{ formatDate(client.createdAt) }}</span>
          <div class="client-actions">
            <button v-if="!client.publicClient" class="btn btn-ghost btn-small" type="button" :disabled="busyId === client.id" @click="rotateSecret(client)">
              <AppIcon name="key" :size="14" /> 轮换密钥
            </button>
            <button class="btn btn-small" :class="client.enabled ? 'btn-ghost-danger' : 'btn-success'" type="button" :disabled="busyId === client.id" @click="toggleEnabled(client)">
              {{ client.enabled ? '停用' : '启用' }}
            </button>
          </div>
        </div>
      </article>
    </section>

    <Teleport to="body">
      <div v-if="secretReveal" class="secret-overlay" @click.self="secretReveal = null">
        <section class="secret-dialog" role="dialog" aria-modal="true" aria-label="客户端密钥">
          <span class="secret-icon"><AppIcon name="key" :size="24" /></span>
          <p class="page-kicker">ONE-TIME SECRET</p>
          <h2>把密钥藏进项圈里</h2>
          <p>这是 <strong>{{ secretReveal.client.name }}</strong> 的客户端密钥。关闭后服务器只保存哈希，无法再次查看。</p>
          <div class="secret-value">
            <code>{{ secretReveal.clientSecret }}</code>
            <button class="btn btn-secondary btn-small" type="button" @click="copy(secretReveal.clientSecret || '', '客户端密钥')">
              <AppIcon name="copy" :size="14" /> 复制
            </button>
          </div>
          <div class="secret-warning"><AppIcon name="alert" :size="15" /> 不要把它写进前端代码或公开仓库。</div>
          <button class="btn btn-primary btn-block" type="button" @click="secretReveal = null">小梦已经记住了</button>
        </section>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.clients-page { max-width: 1280px; }

.clients-heading { align-items: flex-start; }
.clients-heading > div > p:last-child { max-width: 680px; }

.client-overview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 0.7fr)) minmax(240px, 1.25fr);
  gap: 0.65rem;
  margin-bottom: 1rem;
}

.overview-card,
.overview-neko {
  display: flex;
  min-height: 74px;
  align-items: center;
  gap: 0.65rem;
  padding: 0.75rem;
  border: 1px solid var(--border);
  border-radius: 15px;
  background: var(--surface);
  backdrop-filter: blur(12px);
}

.overview-icon {
  display: grid;
  width: 36px;
  height: 36px;
  flex: none;
  place-items: center;
  border-radius: 11px;
}

.overview-icon.pink { color: #ffb0dc; background: rgba(255, 145, 210, 0.09); }
.overview-icon.mint { color: #91f0df; background: rgba(100, 236, 214, 0.08); }
.overview-icon.gold { color: #ffd77d; background: rgba(255, 207, 103, 0.08); }

.overview-card > span:last-child { display: grid; gap: 0.1rem; }
.overview-card strong { color: #f6eafa; font-size: 1.15rem; font-weight: 800; }
.overview-card small { color: #7e6b93; font-size: 0.55rem; }

.overview-neko { overflow: hidden; padding: 0.35rem 0.7rem 0.35rem 0.2rem; }
.overview-neko :deep(.neko-mascot) { width: 66px; flex: none; }
.overview-neko span { color: #967fa9; font-size: 0.58rem; line-height: 1.55; }

.create-panel {
  display: grid;
  grid-template-columns: minmax(230px, 0.58fr) minmax(0, 1.42fr);
  gap: 1.25rem;
  margin: 0 0 1rem;
  padding: 1.15rem;
  border: 1px solid rgba(255, 189, 230, 0.16);
  border-radius: 20px;
  background:
    radial-gradient(circle at 0 0, rgba(255, 155, 213, 0.12), transparent 36%),
    rgba(27, 18, 46, 0.78);
  box-shadow: 0 20px 60px rgba(2, 0, 10, 0.22);
  backdrop-filter: blur(18px);
}

.create-copy h2 { margin-top: 0.42rem; color: #fff4fc; font-size: 1.35rem; font-weight: 780; letter-spacing: -0.05em; }
.create-copy > p:not(.page-kicker) { margin-top: 0.55rem; color: #8b769e; font-size: 0.64rem; line-height: 1.7; }

.create-tip {
  display: flex;
  align-items: flex-start;
  gap: 0.4rem;
  margin-top: 0.9rem;
  padding: 0.58rem 0.62rem;
  border: 1px solid rgba(131, 232, 218, 0.1);
  border-radius: 11px;
  color: #7ebcb6;
  background: rgba(77, 214, 196, 0.035);
  font-size: 0.55rem;
  line-height: 1.55;
}

.create-tip svg { flex: none; }

.create-form { display: grid; gap: 0.72rem; }

.atelier-textarea {
  width: 100%;
  min-height: 78px;
  padding: 0.65rem 0.75rem;
  border: 1px solid rgba(255, 207, 240, 0.1);
  border-radius: 11px;
  outline: 0;
  color: #e9dded;
  background: rgba(7, 4, 16, 0.38);
  font: 0.66rem/1.6 ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  resize: vertical;
}

.atelier-textarea:focus { border-color: rgba(255, 158, 216, 0.47); box-shadow: 0 0 0 3px rgba(255, 145, 210, 0.06); }
.atelier-textarea::placeholder { color: #5f4f72; }

.scope-pills { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 0.4rem; }

.scope-choice {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 0.34rem;
  padding: 0.48rem 0.5rem;
  border: 1px solid rgba(255, 207, 240, 0.08);
  border-radius: 10px;
  color: #716080;
  background: rgba(255, 255, 255, 0.018);
  text-align: left;
  cursor: pointer;
}

.scope-choice.active { border-color: rgba(255, 172, 224, 0.24); color: #f4a9d8; background: rgba(255, 147, 211, 0.07); }
.scope-choice span { display: grid; min-width: 0; }
.scope-choice strong { color: #c9b7d0; font-size: 0.55rem; white-space: nowrap; }
.scope-choice small { overflow: hidden; color: #6c5b7e; font-size: 0.45rem; text-overflow: ellipsis; white-space: nowrap; }
.scope-choice.active small { color: #9d7fa7; }

.client-mode-row { display: flex; align-items: center; justify-content: space-between; gap: 1rem; }

.mode-switch { display: flex; align-items: center; gap: 0.55rem; cursor: pointer; }
.mode-switch input { position: absolute; opacity: 0; pointer-events: none; }
.switch-track { position: relative; width: 36px; height: 20px; flex: none; border-radius: 999px; background: #352846; transition: background 0.18s ease; }
.switch-track i { position: absolute; top: 3px; left: 3px; width: 14px; height: 14px; border-radius: 999px; background: #8a779c; transition: transform 0.18s ease, background 0.18s ease; }
.mode-switch input:checked + .switch-track { background: rgba(255, 138, 205, 0.26); }
.mode-switch input:checked + .switch-track i { background: #ffb0dd; transform: translateX(16px); }
.mode-switch > span:last-child { display: grid; gap: 0.1rem; }
.mode-switch strong { color: #cbb6d0; font-size: 0.59rem; }
.mode-switch small { color: #6d5b7f; font-size: 0.49rem; }

.client-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.75rem; margin-top: 0.8rem; }

.client-card {
  position: relative;
  overflow: hidden;
  padding: 0.95rem;
  border: 1px solid var(--border);
  border-radius: 17px;
  background: var(--surface);
  transition: transform 0.18s ease, border-color 0.18s ease;
}

.client-card::after {
  position: absolute;
  top: -35px;
  right: -35px;
  width: 90px;
  height: 90px;
  border: 1px solid rgba(255, 195, 234, 0.06);
  border-radius: 50%;
  content: '';
}

.client-card:hover { border-color: rgba(255, 175, 225, 0.18); transform: translateY(-2px); }
.client-card.is-disabled { opacity: 0.65; }

.client-card-head { display: flex; align-items: center; gap: 0.68rem; }
.client-avatar { position: relative; display: grid; width: 42px; height: 42px; flex: none; place-items: center; border-radius: 13px; color: #fff6fc; background: linear-gradient(145deg, #ff9ace, #a778ea); font-size: 0.9rem; font-weight: 800; }
.client-avatar::before, .client-avatar::after { position: absolute; top: -4px; width: 9px; height: 10px; border-radius: 4px 6px 0 0; background: inherit; content: ''; }
.client-avatar::before { left: 7px; transform: rotate(-12deg); }
.client-avatar::after { right: 7px; transform: rotate(12deg); }
.client-avatar.confidential { background: linear-gradient(145deg, #7fd9ce, #666ed0); }

.client-title { min-width: 0; flex: 1; }
.client-title > div { display: flex; align-items: center; gap: 0.38rem; }
.client-title h2 { overflow: hidden; color: #f0e5f1; font-size: 0.79rem; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.client-title p { overflow: hidden; margin-top: 0.16rem; color: #7d6b90; font-size: 0.56rem; text-overflow: ellipsis; white-space: nowrap; }
.client-type { flex: none; padding: 0.12rem 0.27rem; border-radius: 999px; color: #9876a3; background: rgba(255, 255, 255, 0.04); font-family: ui-monospace, monospace; font-size: 0.4rem; letter-spacing: 0.04em; }

.client-id-row { display: flex; align-items: center; gap: 0.4rem; margin-top: 0.8rem; padding: 0.55rem 0.6rem; border: 1px dashed rgba(255, 207, 240, 0.1); border-radius: 10px; background: rgba(6, 3, 14, 0.2); }
.client-id-row > span { display: grid; min-width: 0; flex: 1; gap: 0.14rem; }
.client-id-row small { color: #5f4e72; font-size: 0.42rem; letter-spacing: 0.12em; }
.client-id-row code { overflow: hidden; color: #baa4cb; font-family: ui-monospace, monospace; font-size: 0.53rem; text-overflow: ellipsis; white-space: nowrap; }

.client-uri-list { display: grid; gap: 0.16rem; margin-top: 0.65rem; }
.client-uri-list > span { display: inline-flex; align-items: center; gap: 0.3rem; margin-bottom: 0.18rem; color: #6d5b80; font-size: 0.49rem; }
.client-uri-list code { overflow: hidden; color: #9c87ab; font-family: ui-monospace, monospace; font-size: 0.48rem; text-overflow: ellipsis; white-space: nowrap; }
.client-uri-list small { color: #665475; font-size: 0.47rem; }

.client-scopes { display: flex; flex-wrap: wrap; gap: 0.25rem; margin-top: 0.58rem; }
.scope-tag { padding: 0.16rem 0.3rem; border-radius: 999px; color: #ae8ab4; background: rgba(255, 167, 219, 0.055); font-size: 0.44rem; }

.client-card-foot { display: flex; align-items: center; justify-content: space-between; gap: 0.5rem; margin-top: 0.72rem; padding-top: 0.65rem; border-top: 1px solid rgba(255, 255, 255, 0.05); }
.client-date { display: inline-flex; align-items: center; gap: 0.25rem; color: #5d4c70; font-size: 0.47rem; }
.client-actions { display: flex; gap: 0.2rem; }

.clients-empty { min-height: 330px; }
.clients-empty :deep(.neko-mascot) { width: 100px; }
.clients-empty .btn { margin-top: 0.8rem; }

.secret-overlay { position: fixed; z-index: 900; inset: 0; display: grid; place-items: center; padding: 1rem; background: rgba(5, 2, 12, 0.76); backdrop-filter: blur(12px); }
.secret-dialog { width: min(470px, 100%); padding: 1.5rem; border: 1px solid rgba(255, 195, 234, 0.2); border-radius: 22px; background: radial-gradient(circle at 20% 0, rgba(255, 164, 217, 0.12), transparent 40%), #1b122d; box-shadow: 0 35px 100px rgba(0, 0, 0, 0.58); text-align: center; }
.secret-icon { display: grid; width: 54px; height: 54px; margin: 0 auto 0.8rem; place-items: center; border: 1px solid rgba(255, 206, 238, 0.18); border-radius: 17px; color: #ffd17a; background: rgba(255, 205, 108, 0.07); }
.secret-dialog .page-kicker { justify-content: center; }
.secret-dialog h2 { margin-top: 0.4rem; color: #fff1fb; font-size: 1.45rem; font-weight: 800; letter-spacing: -0.05em; }
.secret-dialog > p:not(.page-kicker) { max-width: 370px; margin: 0.55rem auto 0; color: #8f7ba2; font-size: 0.63rem; line-height: 1.65; }
.secret-value { display: flex; align-items: center; gap: 0.44rem; margin-top: 1rem; padding: 0.65rem; border: 1px solid rgba(255, 217, 150, 0.15); border-radius: 11px; background: rgba(7, 4, 15, 0.4); }
.secret-value code { overflow: auto; flex: 1; color: #ffd793; font-family: ui-monospace, monospace; font-size: 0.58rem; text-align: left; white-space: nowrap; }
.secret-warning { display: flex; align-items: center; justify-content: center; gap: 0.35rem; margin-top: 0.7rem; color: #c58c9f; font-size: 0.53rem; }

.atelier-enter-active, .atelier-leave-active { transition: opacity 0.2s ease, transform 0.2s ease; }
.atelier-enter-from, .atelier-leave-to { opacity: 0; transform: translateY(-8px); }

@media (max-width: 1080px) {
  .client-overview { grid-template-columns: repeat(3, 1fr); }
  .overview-neko { display: none; }
  .create-panel { grid-template-columns: 1fr; }
  .scope-pills { grid-template-columns: repeat(3, 1fr); }
  .client-grid { grid-template-columns: 1fr; }
}

@media (max-width: 620px) {
  .client-overview { grid-template-columns: 1fr; }
  .overview-card { min-height: 60px; }
  .scope-pills { grid-template-columns: repeat(2, 1fr); }
  .client-mode-row { align-items: stretch; flex-direction: column; }
  .client-card-foot { align-items: flex-start; flex-direction: column; }
  .clients-heading { flex-direction: column; }
}
</style>
