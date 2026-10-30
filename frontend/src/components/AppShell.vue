<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'
import AppLogo from './AppLogo.vue'
import adminNeko from '@/assets/catgirl/admin-neko.webp'
import { auth } from '@/stores/auth'
import { initials } from '@/lib/format'
import { showToast } from '@/lib/toast'

const route = useRoute()
const router = useRouter()
const mobileOpen = ref(false)
const loggingOut = ref(false)

const navItems = [
  { name: 'admin', to: '/admin', label: '用户名册', icon: 'users', note: '猫屋成员' },
  { name: 'oauth-clients', to: '/admin/clients', label: 'OAuth 应用', icon: 'apps', note: '接入契约' },
]

const pageTitle = computed(() => (
  route.name === 'oauth-clients' ? 'OAuth 应用猫屋' : '用户名册'
))
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
    showToast('已安全离开猫屋，期待下次见面喵', 'success')
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
      <div class="sidebar-glow" aria-hidden="true"></div>
      <div class="sidebar-head">
        <RouterLink to="/admin" class="logo-link" aria-label="FantasyNetwork 管理后台">
          <AppLogo :size="38" />
        </RouterLink>
        <button class="icon-button sidebar-close" type="button" aria-label="关闭菜单" @click="mobileOpen = false">
          <AppIcon name="close" :size="20" />
        </button>
      </div>

      <div class="workspace-chip">
        <span class="workspace-avatar"><AppIcon name="paw" :size="17" /></span>
        <span>
          <strong>梦幻网络猫屋</strong>
          <small>ADMIN CONTROL ROOM</small>
        </span>
        <span class="workspace-live"></span>
      </div>

      <nav class="side-nav" aria-label="主导航">
        <p class="nav-label">猫娘管理席</p>
        <RouterLink
          v-for="item in navItems"
          :key="item.name"
          :to="item.to"
          class="side-link"
          :class="{ 'is-active': route.name === item.name }"
        >
          <span class="side-icon"><AppIcon :name="item.icon" :size="18" /></span>
          <span class="side-copy"><strong>{{ item.label }}</strong><small>{{ item.note }}</small></span>
          <span v-if="route.name === item.name" class="nav-cat-dot">ฅ</span>
        </RouterLink>
      </nav>

      <div class="sidebar-neko-wrap">
        <img :src="adminNeko" class="sidebar-neko-portrait" alt="戴眼镜的猫屋管理员小梦" />
        <div class="sidebar-bubble">小梦已上线，<br />数据都安全喵。</div>
      </div>

      <div class="sidebar-spacer"></div>

      <div class="security-note">
        <span class="security-note-icon"><AppIcon name="shield-cat" :size="18" /></span>
        <div>
          <strong>管理员会话受猫爪保护</strong>
          <p>刷新令牌只走 HttpOnly Cookie，不碰 localStorage。</p>
        </div>
      </div>

      <div class="sidebar-user">
        <span class="user-avatar">{{ avatarText }}</span>
        <span class="user-meta">
          <strong>{{ displayName }}</strong>
          <small>{{ auth.state.user?.role === 'ADMIN' ? '猫屋管理员' : '@' + auth.state.user?.username }}</small>
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
.app-shell { display: flex; min-height: 100svh; }

.sidebar {
  position: fixed;
  z-index: 50;
  inset: 0 auto 0 0;
  display: flex;
  width: 268px;
  flex-direction: column;
  overflow: hidden;
  padding: 1.2rem 0.9rem 1rem;
  border-right: 1px solid rgba(255, 197, 236, 0.1);
  background:
    radial-gradient(circle at 20% 4%, rgba(255, 133, 203, 0.14), transparent 30%),
    radial-gradient(circle at 90% 85%, rgba(101, 228, 215, 0.06), transparent 36%),
    rgba(17, 11, 30, 0.97);
  backdrop-filter: blur(18px);
}

.sidebar-glow {
  position: absolute;
  top: 17%;
  right: -90px;
  width: 200px;
  height: 200px;
  border-radius: 50%;
  background: rgba(225, 133, 255, 0.08);
  filter: blur(35px);
  pointer-events: none;
}

.sidebar > *:not(.sidebar-glow) { position: relative; z-index: 1; }

.sidebar-head { display: flex; align-items: center; justify-content: space-between; padding: 0 0.28rem; }
.logo-link { display: inline-flex; }
.sidebar-close { display: none; }

.workspace-chip {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  margin-top: 1.25rem;
  padding: 0.64rem;
  border: 1px solid rgba(255, 205, 239, 0.1);
  border-radius: 13px;
  background: linear-gradient(115deg, rgba(255, 167, 220, 0.07), rgba(255, 255, 255, 0.018));
}

.workspace-avatar {
  display: grid;
  width: 32px;
  height: 32px;
  flex: none;
  place-items: center;
  border-radius: 10px;
  color: #ffc0e5;
  background: rgba(255, 156, 214, 0.1);
}

.workspace-chip > span:nth-child(2) { display: grid; min-width: 0; flex: 1; gap: 0.13rem; }
.workspace-chip strong { overflow: hidden; color: #ead8eb; font-size: 0.64rem; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.workspace-chip small { color: #695673; font-size: 0.42rem; letter-spacing: 0.11em; }
.workspace-live { width: 6px; height: 6px; flex: none; border-radius: 50%; background: #7ef0dc; box-shadow: 0 0 9px #7ef0dc; }

.side-nav { display: grid; gap: 0.28rem; margin-top: 1.45rem; }
.nav-label { margin: 0 0.55rem 0.38rem; color: #655276; font-size: 0.48rem; font-weight: 700; letter-spacing: 0.13em; }

.side-link {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.57rem;
  border: 1px solid transparent;
  border-radius: 11px;
  color: #8b7798;
  transition: color 0.16s ease, background 0.16s ease, border-color 0.16s ease;
}

.side-link:hover { color: #d8c6dc; background: rgba(255, 231, 248, 0.035); }
.side-link.is-active { border-color: rgba(255, 179, 225, 0.15); color: #ffe9f8; background: linear-gradient(110deg, rgba(255, 150, 211, 0.11), rgba(126, 111, 225, 0.06)); }

.side-icon { display: grid; width: 29px; height: 29px; flex: none; place-items: center; border-radius: 9px; background: rgba(255, 255, 255, 0.035); }
.side-link.is-active .side-icon { color: #ffb2dd; background: rgba(255, 157, 214, 0.09); }
.side-copy { display: grid; min-width: 0; flex: 1; gap: 0.08rem; }
.side-copy strong { font-size: 0.64rem; font-weight: 620; }
.side-copy small { color: #5e4d6d; font-size: 0.45rem; }
.nav-cat-dot { color: #ffacd8; font-size: 0.62rem; }

.sidebar-neko-wrap { position: relative; display: flex; align-items: flex-end; gap: 0.45rem; margin: 1.1rem 0.25rem 0; padding: 0.35rem; border-radius: 14px; background: radial-gradient(circle at 30% 50%, rgba(255, 166, 219, 0.08), transparent 66%); }
.sidebar-neko-portrait { width: 56px; height: 64px; flex: none; border: 1px solid rgba(255, 213, 245, 0.18); border-radius: 12px; object-fit: cover; box-shadow: 0 8px 18px rgba(4, 1, 12, 0.28); }
.sidebar-bubble { position: relative; margin-bottom: 0.7rem; padding: 0.45rem 0.55rem; border: 1px solid rgba(255, 205, 239, 0.12); border-radius: 10px 10px 10px 3px; color: #aa92b8; background: rgba(38, 25, 56, 0.76); font-size: 0.49rem; line-height: 1.5; }

.sidebar-spacer { flex: 1; min-height: 0.8rem; }

.security-note { display: flex; align-items: flex-start; gap: 0.52rem; padding: 0.7rem 0.55rem; border-top: 1px solid rgba(255, 255, 255, 0.055); }
.security-note-icon { display: grid; width: 28px; height: 28px; flex: none; place-items: center; border-radius: 9px; color: #8bded2; background: rgba(92, 229, 209, 0.06); }
.security-note strong { display: block; color: #a894b3; font-size: 0.55rem; font-weight: 650; }
.security-note p { margin-top: 0.14rem; color: #5f4d70; font-size: 0.47rem; line-height: 1.5; }

.sidebar-user { display: flex; align-items: center; gap: 0.55rem; padding: 0.72rem 0.35rem 0.1rem; border-top: 1px solid rgba(255, 255, 255, 0.055); }
.user-avatar { display: grid; width: 34px; height: 34px; flex: none; place-items: center; border-radius: 50%; color: #fff2fb; background: linear-gradient(145deg, #ff9dd1, #8275df); font-size: 0.62rem; font-weight: 750; }
.user-meta { display: grid; min-width: 0; flex: 1; gap: 0.08rem; }
.user-meta strong, .user-meta small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.user-meta strong { color: #d7c6dc; font-size: 0.62rem; font-weight: 650; }
.user-meta small { color: #685776; font-size: 0.49rem; }

.app-main { width: 100%; min-width: 0; margin-left: 268px; }
.content { width: min(1370px, 100%); margin: 0 auto; padding: clamp(1.25rem, 3vw, 2.9rem); }
.mobile-topbar { display: none; }
.sidebar-scrim { display: none; }

@media (max-width: 900px) {
  .sidebar { transform: translateX(-104%); transition: transform 0.22s ease; }
  .sidebar.is-open { transform: translateX(0); }
  .sidebar-close { display: grid; }
  .sidebar-scrim { position: fixed; z-index: 40; inset: 0; display: block; background: rgba(8, 4, 15, 0.7); backdrop-filter: blur(5px); }
  .app-main { margin-left: 0; }
  .mobile-topbar { display: flex; position: sticky; z-index: 30; top: 0; align-items: center; justify-content: space-between; gap: 0.7rem; padding: 0.7rem 1rem; border-bottom: 1px solid rgba(255, 205, 239, 0.08); background: rgba(16, 10, 28, 0.88); backdrop-filter: blur(16px); }
  .mobile-topbar strong { color: #e5d5e8; font-size: 0.72rem; }
  .topbar-avatar { display: grid; width: 29px; height: 29px; place-items: center; border-radius: 50%; color: #fff1fa; background: linear-gradient(145deg, #ff9dd1, #8275df); font-size: 0.52rem; font-weight: 750; }
  .content { padding: 1.15rem 1rem 2rem; }
}
</style>
