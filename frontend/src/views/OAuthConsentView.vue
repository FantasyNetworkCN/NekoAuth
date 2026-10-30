<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import AppLogo from '@/components/AppLogo.vue'
import NekoMascot from '@/components/NekoMascot.vue'
import { ApiError, api } from '@/lib/api'
import { auth } from '@/stores/auth'
import type { OAuthAuthorizeView } from '@/types'

const route = useRoute()
const router = useRouter()
const view = ref<OAuthAuthorizeView | null>(null)
const loading = ref(true)
const deciding = ref(false)
const error = ref('')

const scopeIcons: Record<string, string> = {
  openid: 'shield-cat',
  profile: 'user',
  email: 'mail',
  roles: 'users',
  offline_access: 'refresh',
}

const clientInitial = computed(() => view.value?.clientName.trim().slice(0, 1).toUpperCase() || 'F')
const scopeCount = computed(() => view.value?.scopeDetails.length || 0)

function query(name: string): string {
  const value = route.query[name]
  if (typeof value === 'string') return value
  if (Array.isArray(value)) return value[0] || ''
  return ''
}

function redirectTo(url: string) {
  window.location.assign(url)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await api.oauthAuthorize({
      client_id: query('client_id'),
      redirect_uri: query('redirect_uri'),
      response_type: query('response_type'),
      scope: query('scope'),
      state: query('state'),
      nonce: query('nonce'),
      code_challenge: query('code_challenge'),
      code_challenge_method: query('code_challenge_method'),
      prompt: query('prompt'),
    })
    view.value = result
    if (result.redirectUrl) redirectTo(result.redirectUrl)
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : '无法读取授权申请，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function decide(approved: boolean) {
  if (!view.value || deciding.value) return
  deciding.value = true
  error.value = ''
  try {
    const result = await api.decideOAuthAuthorize({
      clientId: view.value.clientId,
      redirectUri: query('redirect_uri'),
      responseType: query('response_type'),
      scope: view.value.scopes.join(' '),
      state: query('state'),
      nonce: query('nonce'),
      codeChallenge: query('code_challenge'),
      codeChallengeMethod: query('code_challenge_method'),
      prompt: query('prompt'),
      approved,
    })
    redirectTo(result.redirectUrl)
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : '授权操作失败，请稍后重试'
  } finally {
    deciding.value = false
  }
}

async function switchAccount() {
  const target = route.fullPath
  await auth.logout()
  await router.replace({ name: 'login', query: { redirect: target } })
}

onMounted(() => void load())
</script>

<template>
  <div class="consent-page">
    <div class="consent-stars" aria-hidden="true"><span>✦</span><span>✧</span><span>♡</span><span>🐾</span></div>

    <header class="consent-header">
      <AppLogo :size="40" />
      <span class="secure-badge"><AppIcon name="lock" :size="14" /> OAuth 2.0 安全授权</span>
    </header>

    <main class="consent-layout">
      <section class="consent-art">
        <div class="art-copy">
          <p class="page-kicker"><AppIcon name="spark" :size="14" /> NEKO CONSENT GARDEN</p>
          <h1>猫娘小梦<br /><span>在此守候喵。</span></h1>
          <p>每一个权限都会先经过你的猫爪印。看清楚申请内容，再决定要不要把身份交给它。</p>
        </div>
        <div class="art-stage">
          <span class="art-halo"></span>
          <NekoMascot pose="guard" />
          <div class="art-bubble">
            <strong>“身份安全，就交给小梦吧。”</strong>
            <span>绝不把主人卖给出价更高的罐头！</span>
          </div>
          <span class="art-sticker"><AppIcon name="shield-cat" :size="18" /> PKCE 已启用</span>
        </div>
        <div class="art-paws" aria-hidden="true">
          <span>🐾</span><span>ฅ^•ﻌ•^ฅ</span><span>♡</span>
        </div>
      </section>

      <section class="consent-panel">
        <div v-if="loading" class="consent-state">
          <NekoMascot pose="sleep" compact />
          <span class="spinner"></span>
          <h2>小梦正在核对猫爪印…</h2>
          <p>正在验证应用、回调地址与 PKCE 参数。</p>
        </div>

        <div v-else-if="error && !view" class="consent-state is-error">
          <span class="state-badge"><AppIcon name="alert" :size="24" /></span>
          <h2>这份授权请求有问题</h2>
          <p>{{ error }}</p>
          <button class="btn btn-secondary" type="button" @click="router.replace('/')">回到梦幻网络</button>
        </div>

        <template v-else-if="view">
          <div class="consent-title-row">
            <div>
              <p class="page-kicker">AUTHORIZATION REQUEST</p>
              <h2>允许它遇见你吗？</h2>
              <p><strong>{{ view.clientName }}</strong> 正在请求访问你的 Neko ID。</p>
            </div>
            <span class="consent-step">01 / 01</span>
          </div>

          <article class="app-card">
            <span class="app-avatar">{{ clientInitial }}</span>
            <div class="app-meta">
              <div><h3>{{ view.clientName }}</h3><span class="verified-tag"><AppIcon name="check" :size="11" /> 已登记</span></div>
              <p>{{ view.description || '来自梦幻网络生态的 OAuth 应用' }}</p>
              <span class="app-host"><AppIcon name="globe" :size="13" /> {{ view.redirectHost }}</span>
            </div>
          </article>

          <div class="identity-line">
            <span class="identity-avatar">{{ view.displayName.slice(0, 1).toUpperCase() }}</span>
            <span><small>以梦幻网络身份授权</small><strong>{{ view.displayName }} <em>@{{ view.username }}</em></strong></span>
            <button class="btn btn-ghost btn-small" type="button" @click="switchAccount">切换账户</button>
          </div>

          <div v-if="view.previouslyApproved" class="notice-banner">
            <AppIcon name="heart" :size="16" />
            <span>你之前已经允许过这个应用，本次将会刷新授权记录。</span>
          </div>

          <section class="scope-section">
            <div class="scope-heading">
              <span><AppIcon name="key" :size="16" /> 申请的权限</span>
              <small>{{ scopeCount }} 项</small>
            </div>
            <ul class="scope-list">
              <li v-for="scope in view.scopeDetails" :key="scope.name">
                <span class="scope-icon"><AppIcon :name="scopeIcons[scope.name] || 'spark'" :size="18" /></span>
                <span><strong>{{ scope.label }}</strong><small>{{ scope.description }}</small></span>
                <span class="scope-check"><AppIcon name="check" :size="13" /></span>
              </li>
            </ul>
          </section>

          <div v-if="error" class="alert alert-error" role="alert">
            <AppIcon name="alert" :size="17" /><span>{{ error }}</span>
          </div>

          <div class="consent-actions">
            <button class="btn btn-secondary" type="button" :disabled="deciding" @click="decide(false)">拒绝喵</button>
            <button class="btn btn-primary" type="button" :disabled="deciding" @click="decide(true)">
              <span v-if="deciding" class="spinner"></span>
              <template v-else><AppIcon name="paw" :size="17" /> 盖上猫爪印，允许</template>
            </button>
          </div>

          <p class="consent-footnote">
            <AppIcon name="shield" :size="13" />
            你随时可以在管理员猫屋停用应用。授权不会共享你的密码。
          </p>
        </template>
      </section>
    </main>
  </div>
</template>

<style scoped>
.consent-page {
  position: relative;
  min-height: 100svh;
  overflow: hidden;
  background:
    radial-gradient(circle at 10% 15%, rgba(249, 133, 200, 0.16), transparent 30%),
    radial-gradient(circle at 91% 5%, rgba(102, 226, 217, 0.09), transparent 28%),
    #0e091a;
}

.consent-stars {
  position: absolute;
  inset: 0;
  color: rgba(255, 206, 240, 0.42);
  pointer-events: none;
}

.consent-stars span { position: absolute; }
.consent-stars span:nth-child(1) { top: 8%; left: 46%; font-size: 1.4rem; }
.consent-stars span:nth-child(2) { top: 19%; right: 5%; }
.consent-stars span:nth-child(3) { bottom: 17%; left: 3%; color: #ffa7d7; font-size: 1.8rem; }
.consent-stars span:nth-child(4) { bottom: 9%; right: 31%; opacity: 0.55; }

.consent-header {
  position: relative;
  z-index: 4;
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: min(1330px, calc(100% - 2.5rem));
  margin: 0 auto;
  padding: 1.35rem 0 0.5rem;
}

.secure-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.42rem 0.66rem;
  border: 1px solid rgba(255, 193, 232, 0.14);
  border-radius: 999px;
  color: #bda9cf;
  background: rgba(255, 226, 247, 0.04);
  font-size: 0.56rem;
  font-weight: 650;
}

.consent-layout {
  position: relative;
  z-index: 2;
  display: grid;
  width: min(1330px, calc(100% - 2.5rem));
  min-height: calc(100svh - 90px);
  grid-template-columns: minmax(360px, 0.88fr) minmax(450px, 1.12fr);
  align-items: center;
  gap: clamp(1rem, 4vw, 4rem);
  margin: 0 auto;
  padding: 1rem 0 2.2rem;
}

.consent-art {
  display: grid;
  align-content: center;
  gap: 1rem;
}

.page-kicker {
  display: flex;
  align-items: center;
  gap: 0.42rem;
  color: #d6a1c6;
  font-size: 0.53rem;
  font-weight: 800;
  letter-spacing: 0.15em;
}

.art-copy h1 {
  margin-top: 0.55rem;
  color: #fff8fd;
  font-size: clamp(2rem, 4vw, 3.35rem);
  font-weight: 820;
  letter-spacing: -0.07em;
  line-height: 1.02;
}

.art-copy h1 span {
  color: transparent;
  background: linear-gradient(100deg, #ffb1dd, #fff3fc 55%, #98f4e6);
  background-clip: text;
}

.art-copy > p:last-child {
  max-width: 430px;
  margin-top: 0.7rem;
  color: #9d8aaf;
  font-size: 0.68rem;
  line-height: 1.75;
}

.art-stage {
  position: relative;
  display: grid;
  min-height: 330px;
  place-items: center;
}

.art-stage :deep(.neko-mascot) {
  position: relative;
  z-index: 1;
  width: min(88%, 390px);
  max-height: 380px;
}

.art-halo {
  position: absolute;
  width: min(72%, 340px);
  aspect-ratio: 1;
  border: 1px solid rgba(255, 204, 238, 0.2);
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 187, 230, 0.13), transparent 64%);
  box-shadow: 0 0 70px rgba(231, 117, 204, 0.1);
}

.art-bubble {
  position: absolute;
  z-index: 2;
  right: 0;
  bottom: 9%;
  display: grid;
  gap: 0.15rem;
  max-width: 190px;
  padding: 0.66rem 0.8rem;
  border: 1px solid rgba(255, 215, 243, 0.45);
  border-radius: 15px 15px 15px 4px;
  color: #f5ddef;
  background: rgba(43, 25, 64, 0.78);
  box-shadow: 0 14px 36px rgba(6, 2, 16, 0.25);
  font-size: 0.55rem;
  line-height: 1.55;
  backdrop-filter: blur(12px);
}

.art-bubble strong { color: #ffeefb; font-size: 0.61rem; }
.art-bubble span { color: #a995bc; font-size: 0.51rem; }

.art-sticker {
  position: absolute;
  z-index: 2;
  left: 1%;
  top: 36%;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.42rem 0.58rem;
  border: 1px solid rgba(155, 246, 232, 0.2);
  border-radius: 999px;
  color: #a2f3e7;
  background: rgba(70, 214, 195, 0.075);
  font-size: 0.52rem;
  transform: rotate(-4deg);
}

.art-paws {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  color: #79658f;
  font-size: 0.63rem;
}

.art-paws span:nth-child(2) { color: #9b86ad; font-size: 0.58rem; }

.consent-panel {
  position: relative;
  padding: clamp(1.2rem, 3.3vw, 2.1rem);
  border: 1px solid rgba(255, 207, 240, 0.15);
  border-radius: 26px;
  background:
    linear-gradient(145deg, rgba(255, 235, 249, 0.06), transparent 45%),
    rgba(24, 16, 42, 0.84);
  box-shadow: 0 30px 90px rgba(2, 0, 10, 0.42), inset 0 1px 0 rgba(255, 255, 255, 0.07);
  backdrop-filter: blur(22px);
}

.consent-panel::before {
  position: absolute;
  inset: 9px;
  border: 1px solid rgba(255, 207, 240, 0.045);
  border-radius: 19px;
  content: '';
  pointer-events: none;
}

.consent-title-row {
  position: relative;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.consent-title-row h2 {
  margin-top: 0.35rem;
  color: #fff7fd;
  font-size: clamp(1.55rem, 2.5vw, 2.2rem);
  font-weight: 800;
  letter-spacing: -0.06em;
}

.consent-title-row p:last-child {
  margin-top: 0.35rem;
  color: #9683aa;
  font-size: 0.66rem;
}

.consent-title-row p:last-child strong { color: #e7cce3; }

.consent-step {
  flex: none;
  color: #6f5b85;
  font-family: ui-monospace, monospace;
  font-size: 0.54rem;
}

.app-card {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  margin-top: 1.1rem;
  padding: 0.9rem;
  border: 1px solid rgba(255, 204, 239, 0.1);
  border-radius: 17px;
  background: rgba(255, 239, 251, 0.035);
}

.app-avatar {
  display: grid;
  width: 52px;
  height: 52px;
  flex: none;
  place-items: center;
  border: 1px solid rgba(255, 220, 245, 0.26);
  border-radius: 16px;
  color: #fff;
  background: linear-gradient(145deg, #ff9fd2, #ad7cf0);
  box-shadow: 0 9px 24px rgba(219, 109, 196, 0.22);
  font-size: 1.25rem;
  font-weight: 850;
}

.app-meta { min-width: 0; flex: 1; }
.app-meta > div { display: flex; align-items: center; gap: 0.45rem; }
.app-meta h3 { overflow: hidden; color: #f4e7f4; font-size: 0.82rem; font-weight: 710; text-overflow: ellipsis; white-space: nowrap; }
.app-meta p { overflow: hidden; margin-top: 0.17rem; color: #8c789f; font-size: 0.6rem; text-overflow: ellipsis; white-space: nowrap; }

.verified-tag {
  display: inline-flex;
  align-items: center;
  gap: 0.15rem;
  padding: 0.13rem 0.3rem;
  border-radius: 999px;
  color: #94eddd;
  background: rgba(91, 228, 210, 0.08);
  font-size: 0.46rem;
}

.app-host {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  margin-top: 0.34rem;
  color: #69577f;
  font-size: 0.51rem;
}

.identity-line {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  margin-top: 0.65rem;
  padding: 0.55rem 0.65rem;
  border: 1px dashed rgba(255, 204, 239, 0.11);
  border-radius: 12px;
}

.identity-avatar {
  display: grid;
  width: 30px;
  height: 30px;
  flex: none;
  place-items: center;
  border-radius: 11px;
  color: #fff1fb;
  background: linear-gradient(145deg, #6d5bb0, #3c2e67);
  font-size: 0.62rem;
  font-weight: 750;
}

.identity-line > span:nth-child(2) { display: grid; min-width: 0; flex: 1; }
.identity-line small { color: #69577f; font-size: 0.48rem; }
.identity-line strong { overflow: hidden; margin-top: 0.1rem; color: #d4c3db; font-size: 0.63rem; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.identity-line em { color: #846d99; font-size: 0.55rem; font-style: normal; }

.scope-section { margin-top: 1rem; }

.scope-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #cbb7d1;
  font-size: 0.63rem;
  font-weight: 650;
}

.scope-heading > span { display: inline-flex; align-items: center; gap: 0.38rem; }
.scope-heading small { color: #6e5b83; font-size: 0.52rem; }

.scope-list {
  display: grid;
  gap: 0.42rem;
  margin-top: 0.55rem;
  padding: 0;
  list-style: none;
}

.scope-list li {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.58rem 0.62rem;
  border: 1px solid rgba(255, 207, 240, 0.075);
  border-radius: 12px;
  background: rgba(255, 252, 255, 0.022);
}

.scope-icon {
  display: grid;
  width: 32px;
  height: 32px;
  flex: none;
  place-items: center;
  border-radius: 10px;
  color: #f8b2df;
  background: rgba(255, 156, 213, 0.08);
}

.scope-list li > span:nth-child(2) { display: grid; gap: 0.16rem; min-width: 0; flex: 1; }
.scope-list strong { color: #ddcee0; font-size: 0.63rem; font-weight: 650; }
.scope-list small { color: #756289; font-size: 0.53rem; line-height: 1.45; }

.scope-check {
  display: grid;
  width: 20px;
  height: 20px;
  flex: none;
  place-items: center;
  border-radius: 999px;
  color: #8ee6d8;
  background: rgba(78, 220, 198, 0.09);
}

.consent-actions {
  display: grid;
  grid-template-columns: 0.72fr 1.28fr;
  gap: 0.55rem;
  margin-top: 1rem;
}

.consent-actions .btn { min-height: 44px; }

.consent-footnote {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.3rem;
  margin-top: 0.72rem;
  color: #5f4d73;
  font-size: 0.5rem;
  line-height: 1.55;
  text-align: center;
}

.consent-state {
  display: grid;
  min-height: 430px;
  place-items: center;
  align-content: center;
  gap: 0.7rem;
  text-align: center;
}

.consent-state :deep(.neko-mascot) { width: 130px; }
.consent-state h2 { color: #f6e7f4; font-size: 1.1rem; }
.consent-state p { max-width: 330px; color: #8e789f; font-size: 0.65rem; line-height: 1.6; }
.consent-state .btn { margin-top: 0.6rem; }

.state-badge {
  display: grid;
  width: 52px;
  height: 52px;
  place-items: center;
  border: 1px solid rgba(255, 118, 157, 0.2);
  border-radius: 16px;
  color: #ff8cad;
  background: rgba(255, 111, 153, 0.08);
}

@media (max-width: 980px) {
  .consent-layout {
    grid-template-columns: 1fr;
    width: min(680px, calc(100% - 2rem));
    gap: 0.5rem;
    padding-top: 0.2rem;
  }
  .consent-art { display: none; }
  .consent-panel { margin-bottom: 2rem; }
}

@media (max-width: 560px) {
  .consent-header { width: calc(100% - 1.4rem); padding-top: 1rem; }
  .secure-badge { display: none; }
  .consent-layout { width: calc(100% - 1rem); }
  .consent-panel { padding: 1.05rem; border-radius: 20px; }
  .consent-title-row h2 { font-size: 1.5rem; }
  .consent-step { display: none; }
  .consent-actions { grid-template-columns: 1fr; }
  .consent-actions .btn-secondary { order: 2; }
}
</style>
