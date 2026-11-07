<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'
import AppLogo from './AppLogo.vue'
import adminNeko from '@/assets/local/admin-neko.webp'
import { auth } from '@/stores/auth'
import { initials } from '@/lib/format'
import { showToast } from '@/lib/toast'

const route = useRoute()
const router = useRouter()
const mobileOpen = ref(false)
const loggingOut = ref(false)

const navItems = [
  { name: 'admin', to: '/admin', label: '用户名册', icon: 'users', note: 'MEMBERS' },
  { name: 'oauth-clients', to: '/admin/clients', label: 'OAuth 应用', icon: 'apps', note: 'CLIENTS' },
  { name: 'admin-settings', to: '/admin/settings', label: '系统设置', icon: 'sliders', note: 'SYSTEM' },
]

const pageTitle = computed(() => {
  if (route.name === 'oauth-clients') return 'OAuth 应用'
  if (route.name === 'admin-settings') return '系统设置'
  return '用户名册'
})
const displayName = computed(() => auth.state.user?.displayName || auth.state.user?.username || '访客')
const avatarText = computed(() => initials(displayName.value))

watch(
  () => route.fullPath,
  () => {
    mobileOpen.value = false
  },
)

async function logout() {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    await auth.logout()
    showToast('已安全退出梦幻网络', 'success')
    await router.push({ name: 'login' })
  } catch {
    showToast('退出请求失败，但本地会话已清除', 'info')
    await router.push({ name: 'login' })
  } finally {
    loggingOut.value = false
  }
}
</script>

<template>
  <div class="app-shell">
    <div v-if="mobileOpen" class="sidebar-scrim" @click="mobileOpen = false"></div>

    <aside class="sidebar" :class="{ 'is-open': mobileOpen }">
      <div class="sidebar-aurora" aria-hidden="true"></div>
      <header class="sidebar-head">
        <RouterLink to="/admin" class="logo-link" aria-label="梦幻网络管理后台">
          <AppLogo :size="38" />
        </RouterLink>
        <button class="icon-button sidebar-close" type="button" aria-label="关闭菜单" @click="mobileOpen = false">
          <AppIcon name="close" :size="20" />
        </button>
      </header>

      <div class="workspace-chip">
        <span class="workspace-avatar"><AppIcon name="paw" :size="17" /></span>
        <span>
          <strong>梦幻网络控制台</strong>
          <small>FANTASY NETWORK CONSOLE</small>
        </span>
        <i></i>
      </div>

      <nav class="side-nav" aria-label="主导航">
        <p class="nav-label">CONTROL ROOM</p>
        <RouterLink
          v-for="item in navItems"
          :key="item.name"
          :to="item.to"
          class="side-link"
          :class="{ 'is-active': route.name === item.name }"
        >
          <span class="side-icon"><AppIcon :name="item.icon" :size="18" /></span>
          <span class="side-copy"><strong>{{ item.label }}</strong><small>{{ item.note }}</small></span>
          <span v-if="route.name === item.name" class="nav-signal"></span>
        </RouterLink>
      </nav>

      <div class="sidebar-neko-wrap">
        <img :src="adminNeko" class="sidebar-neko-portrait" alt="梦幻网络猫娘管理员" />
        <div class="sidebar-bubble">
          <strong>今天也一起守护星海。</strong>
          <span>账户与授权状态实时同步中。</span>
        </div>
      </div>

      <div class="sidebar-spacer"></div>

      <div class="security-note">
        <span><AppIcon name="shield-cat" :size="18" /></span>
        <div>
          <strong>会话保护已启用</strong>
          <p>刷新令牌仅通过 HttpOnly Cookie 传输。</p>
        </div>
      </div>

      <div class="sidebar-user">
        <span class="user-avatar">{{ avatarText }}</span>
        <span class="user-meta">
          <strong>{{ displayName }}</strong>
          <small>{{ auth.state.user?.role === 'ADMIN' ? '管理员' : '@' + auth.state.user?.username }}</small>
        </span>
        <button class="icon-button" type="button" aria-label="退出登录" :disabled="loggingOut" @click="logout">
          <AppIcon name="logout" :size="18" />
        </button>
      </div>
    </aside>

    <div class="app-main">
      <header class="mobile-topbar">
        <button class="icon-button" type="button" aria-label="打开菜单" @click="mobileOpen = true">
          <AppIcon name="menu" :size="21" />
        </button>
        <strong>{{ pageTitle }}</strong>
        <span class="topbar-avatar">{{ avatarText }}</span>
      </header>

      <main class="content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  min-height: 100svh;
  background: var(--bg);
}

.sidebar {
  position: fixed;
  z-index: 50;
  inset: 0 auto 0 0;
  display: flex;
  width: 278px;
  flex-direction: column;
  overflow: hidden;
  padding: 1.15rem 0.9rem 1rem;
  border-right: 1px solid var(--line);
  background:
    radial-gradient(circle at 15% 0%, rgba(239, 98, 98, 0.055), transparent 30%),
    rgba(9, 9, 11, 0.97);
  backdrop-filter: blur(24px);
}

.sidebar-aurora {
  position: absolute;
  top: 25%;
  right: -150px;
  width: 260px;
  height: 260px;
  border-radius: 50%;
  background: var(--accent-2);
  filter: blur(80px);
  opacity: 0.09;
  pointer-events: none;
}

.sidebar > *:not(.sidebar-aurora) { position: relative; z-index: 1; }

.sidebar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.1rem 0.25rem;
}

.logo-link { display: inline-flex; min-width: 0; }
.sidebar-close { display: none; }

.workspace-chip {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin-top: 1.35rem;
  padding: 0.65rem;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: rgba(18, 22, 33, 0.72);
}

.workspace-avatar {
  display: grid;
  width: 33px;
  height: 33px;
  flex: none;
  place-items: center;
  border-radius: 10px;
  color: var(--accent);
  background: rgba(239, 98, 98, 0.08);
}

.workspace-chip > span:nth-child(2) { display: grid; min-width: 0; flex: 1; gap: 0.14rem; }
.workspace-chip strong {
  overflow: hidden;
  color: var(--ink);
  font-size: 0.66rem;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.workspace-chip small {
  overflow: hidden;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.41rem;
  letter-spacing: 0.08em;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.workspace-chip i {
  width: 5px;
  height: 5px;
  flex: none;
  border-radius: 50%;
  background: var(--success);
  box-shadow: 0 0 10px rgba(129, 239, 197, 0.65);
}

.side-nav { display: grid; gap: 0.32rem; margin-top: 1.5rem; }
.nav-label {
  margin: 0 0.62rem 0.42rem;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.47rem;
  font-weight: 700;
  letter-spacing: 0.15em;
}

.side-link {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.58rem;
  border: 1px solid transparent;
  border-radius: 11px;
  color: var(--body);
  transition:
    color var(--dur-fast) ease,
    background var(--dur-fast) ease,
    border-color var(--dur-fast) ease,
    transform var(--dur-fast) var(--ease-out);
}

.side-link:hover {
  color: var(--ink);
  background: rgba(255, 255, 255, 0.035);
}

.side-link.is-active {
  border-color: rgba(239, 98, 98, 0.18);
  color: var(--ink);
  background:
    linear-gradient(100deg, rgba(239, 98, 98, 0.1), rgba(226, 180, 92, 0.035));
}

.side-link.is-active::before {
  position: absolute;
  left: -1px;
  width: 2px;
  height: 20px;
  border-radius: 999px;
  background: var(--accent);
  box-shadow: 0 0 12px rgba(239, 98, 98, 0.62);
  content: '';
}

.side-icon {
  display: grid;
  width: 29px;
  height: 29px;
  flex: none;
  place-items: center;
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.035);
}

.side-link.is-active .side-icon {
  color: var(--accent);
  background: rgba(239, 98, 98, 0.09);
}

.side-copy { display: grid; min-width: 0; flex: 1; gap: 0.08rem; }
.side-copy strong { font-size: 0.67rem; font-weight: 620; }
.side-copy small {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 0.42rem;
  letter-spacing: 0.06em;
}
.nav-signal {
  width: 5px;
  height: 5px;
  flex: none;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 9px rgba(239, 98, 98, 0.7);
}

.sidebar-neko-wrap {
  position: relative;
  display: flex;
  align-items: flex-end;
  gap: 0.55rem;
  margin: 1.2rem 0.2rem 0;
  padding: 0.35rem;
  border-radius: 16px;
  background: radial-gradient(circle at 28% 45%, rgba(239, 98, 98, 0.07), transparent 68%);
}

.sidebar-neko-portrait {
  width: 64px;
  height: 78px;
  flex: none;
  border: 1px solid var(--line);
  border-radius: 13px;
  object-fit: cover;
  object-position: 50% 20%;
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.35);
}

.sidebar-bubble {
  position: relative;
  display: grid;
  gap: 0.15rem;
  margin-bottom: 0.8rem;
  padding: 0.48rem 0.56rem;
  border: 1px solid var(--line);
  border-radius: 10px 10px 10px 3px;
  background: rgba(18, 22, 33, 0.9);
}
.sidebar-bubble strong { color: var(--body); font-size: 0.5rem; font-weight: 650; line-height: 1.45; }
.sidebar-bubble span { color: var(--muted); font-size: 0.44rem; line-height: 1.45; }

.sidebar-spacer { flex: 1; min-height: 0.8rem; }

.security-note {
  display: flex;
  align-items: flex-start;
  gap: 0.52rem;
  padding: 0.75rem 0.5rem;
  border-top: 1px solid var(--line-soft);
}
.security-note > span {
  display: grid;
  width: 28px;
  height: 28px;
  flex: none;
  place-items: center;
  border-radius: 9px;
  color: var(--success);
  background: rgba(129, 239, 197, 0.07);
}
.security-note strong { display: block; color: var(--body); font-size: 0.56rem; font-weight: 650; }
.security-note p { margin-top: 0.14rem; color: var(--muted); font-size: 0.47rem; line-height: 1.5; }

.sidebar-user {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.72rem 0.3rem 0.05rem;
  border-top: 1px solid var(--line-soft);
}
.user-avatar {
  display: grid;
  width: 34px;
  height: 34px;
  flex: none;
  place-items: center;
  border: 1px solid rgba(239, 98, 98, 0.2);
  border-radius: 50%;
  color: var(--ink);
  background: linear-gradient(145deg, #412b31, #1d1a20);
  font-size: 0.62rem;
  font-weight: 750;
}
.user-meta { display: grid; min-width: 0; flex: 1; gap: 0.08rem; }
.user-meta strong,
.user-meta small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.user-meta strong { color: var(--body); font-size: 0.62rem; font-weight: 650; }
.user-meta small { color: var(--muted); font-size: 0.49rem; }

.app-main {
  width: 100%;
  min-width: 0;
  margin-left: 278px;
}

.content {
  width: min(1420px, 100%);
  margin: 0 auto;
  padding: clamp(1.3rem, 3vw, 3rem);
}

.mobile-topbar { display: none; }
.sidebar-scrim { display: none; }

@media (max-width: 900px) {
  .sidebar {
    transform: translateX(-104%);
    transition: transform var(--dur-base) var(--ease-out);
  }
  .sidebar.is-open { transform: translateX(0); }
  .sidebar-close { display: grid; }
  .sidebar-scrim {
    position: fixed;
    z-index: 40;
    inset: 0;
    display: block;
    background: rgba(0, 0, 0, 0.72);
    backdrop-filter: blur(6px);
  }
  .app-main { margin-left: 0; }
  .mobile-topbar {
    position: sticky;
    z-index: 30;
    top: 0;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 0.7rem;
    padding: 0.7rem 1rem;
    border-bottom: 1px solid var(--line);
    background: rgba(9, 9, 11, 0.9);
    backdrop-filter: blur(20px);
  }
  .mobile-topbar strong { color: var(--ink); font-size: 0.72rem; }
  .topbar-avatar {
    display: grid;
    width: 30px;
    height: 30px;
    place-items: center;
    border: 1px solid rgba(239, 98, 98, 0.2);
    border-radius: 50%;
    color: var(--ink);
    background: linear-gradient(145deg, #412b31, #1d1a20);
    font-size: 0.52rem;
    font-weight: 750;
  }
  .content { padding: 1.15rem 1rem 2rem; }
}
</style>
