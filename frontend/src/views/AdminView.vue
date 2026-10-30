<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import NekoMascot from '@/components/NekoMascot.vue'
import { api, ApiError } from '@/lib/api'
import { formatDate, initials } from '@/lib/format'
import { showToast } from '@/lib/toast'
import { auth } from '@/stores/auth'
import type { PageResponse, Role, User } from '@/types'

const query = ref('')
const result = ref<PageResponse<User>>({
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
})
const loading = ref(true)
const error = ref('')
const busyId = ref<number | null>(null)

const currentUserId = computed(() => auth.state.user?.id)
const hasPrevious = computed(() => result.value.page > 0)
const hasNext = computed(() => result.value.page + 1 < result.value.totalPages)

async function load(page = 0) {
  loading.value = true
  error.value = ''
  try {
    result.value = await api.users(query.value.trim(), page, result.value.size)
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : '无法加载用户列表'
  } finally {
    loading.value = false
  }
}

function search() {
  void load(0)
}

async function updateUser(user: User, patch: { enabled?: boolean; role?: Role }) {
  if (busyId.value !== null) return
  busyId.value = user.id
  try {
    const updated = await api.updateUser(user.id, patch)
    const index = result.value.content.findIndex((item) => item.id === updated.id)
    if (index >= 0) result.value.content[index] = updated
    showToast(`已更新 ${updated.displayName} 的账户设置`, 'success')
  } catch (caught) {
    showToast(caught instanceof ApiError ? caught.message : '更新用户失败', 'error')
  } finally {
    busyId.value = null
  }
}

function onRoleChange(user: User, event: Event) {
  const target = event.target as HTMLSelectElement
  const role = target.value as Role
  if (role === user.role) return
  void updateUser(user, { role })
}

async function toggleEnabled(user: User) {
  await updateUser(user, { enabled: !user.enabled })
}

onMounted(() => void load(0))
</script>

<template>
  <div class="page">
    <header class="page-heading">
      <div>
        <p class="page-kicker"><AppIcon name="paw" :size="13" /> NEKO USER REGISTRY</p>
        <h1>猫娘与成员名册</h1>
        <p>查看梦幻网络的成员、调整阵营或让异常账户暂时打盹。角色与状态变化会立刻让旧令牌失效喵。</p>
      </div>
      <div class="admin-heading-ornament">
        <NekoMascot pose="wink" compact />
        <span class="admin-mark"><AppIcon name="shield-cat" :size="18" /> 猫屋管理员</span>
      </div>
    </header>

    <section class="admin-toolbar">
      <form class="search-box" @submit.prevent="search">
        <AppIcon name="search" :size="18" />
        <input v-model="query" type="search" placeholder="搜索成员名、邮箱或昵称" autocomplete="off" />
        <button class="btn btn-secondary btn-small" type="submit" :disabled="loading">搜索</button>
      </form>
      <div class="user-count">
        <strong>{{ result.totalElements }}</strong>
        <span>位用户</span>
      </div>
      <button class="icon-button" type="button" aria-label="刷新用户列表" :disabled="loading" @click="load(result.page)">
        <AppIcon name="refresh" :size="18" />
      </button>
    </section>

    <div v-if="error" class="alert alert-error" role="alert">
      <AppIcon name="alert" :size="18" /> {{ error }}
      <button class="alert-action" type="button" @click="load(0)">重试</button>
    </div>

    <div v-if="loading" class="admin-loading">
      <span class="spinner"></span>
      正在读取用户…
    </div>

    <div v-else-if="result.content.length === 0" class="empty-state panel">
      <span class="empty-icon"><AppIcon name="users" :size="28" /></span>
      <h2>名册里没有这只猫娘</h2>
      <p>换个关键词，或者确认她是不是躲进了别的世界观。</p>
    </div>

    <section v-else class="user-grid" aria-label="用户列表">
      <article v-for="user in result.content" :key="user.id" class="user-card" :class="{ 'is-self': user.id === currentUserId }">
        <div class="user-card-head">
          <span class="user-card-avatar" :class="{ 'is-admin': user.role === 'ADMIN' }">{{ initials(user.displayName) }}</span>
          <div class="user-card-name">
            <div>
              <h2>{{ user.displayName }}</h2>
              <span v-if="user.id === currentUserId" class="self-tag">你</span>
            </div>
            <p>@{{ user.username }} · #{{ user.id }}</p>
          </div>
          <span class="status-pill" :class="user.enabled ? 'is-online' : 'is-offline'">
            <span></span>{{ user.enabled ? '营业中' : '打盹中' }}
          </span>
        </div>

        <p class="user-email"><AppIcon name="mail" :size="14" /> {{ user.email }}</p>

        <div class="user-facts">
          <span><small>加入时间</small><strong>{{ formatDate(user.createdAt) }}</strong></span>
          <span><small>最近登录</small><strong>{{ formatDate(user.lastLoginAt) }}</strong></span>
        </div>

        <div class="user-controls">
          <label class="role-select">
            <span>角色</span>
            <select :value="user.role" :disabled="user.id === currentUserId || busyId !== null" @change="onRoleChange(user, $event)">
              <option value="USER">普通成员</option>
              <option value="ADMIN">管理员</option>
            </select>
          </label>

          <button
            class="btn btn-small"
            :class="user.enabled ? 'btn-ghost-danger' : 'btn-success'"
            type="button"
            :disabled="user.id === currentUserId || busyId !== null"
            @click="toggleEnabled(user)"
          >
            <span v-if="busyId === user.id" class="spinner"></span>
            <template v-else>
              <AppIcon :name="user.enabled ? 'close' : 'check'" :size="15" />
              {{ user.enabled ? '停用' : '启用' }}
            </template>
          </button>
        </div>
      </article>
    </section>

    <nav v-if="result.totalPages > 1" class="pagination" aria-label="用户列表分页">
      <button class="btn btn-ghost btn-small" type="button" :disabled="!hasPrevious || loading" @click="load(result.page - 1)">
        <AppIcon name="chevron-left" :size="16" /> 上一页
      </button>
      <span>第 {{ result.page + 1 }} / {{ result.totalPages }} 页</span>
      <button class="btn btn-ghost btn-small" type="button" :disabled="!hasNext || loading" @click="load(result.page + 1)">
        下一页 <AppIcon name="chevron-right" :size="16" />
      </button>
    </nav>
  </div>
</template>

<style scoped>
.admin-heading-ornament {
  display: flex;
  flex: none;
  align-items: center;
  gap: 0.35rem;
}

.admin-heading-ornament :deep(.neko-mascot) {
  width: 78px;
  margin: -1.1rem -0.3rem -0.8rem 0;
}

.admin-mark {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.45rem 0.7rem;
  border: 1px solid rgba(66, 232, 197, 0.13);
  border-radius: 999px;
  color: var(--accent);
  background: rgba(66, 232, 197, 0.04);
  font-size: 0.68rem;
  font-weight: 600;
}

@media (max-width: 680px) {
  .admin-heading-ornament :deep(.neko-mascot) { display: none; }
}

.admin-toolbar {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  margin-top: 1.7rem;
}

.search-box {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 0.55rem;
  padding: 0.35rem 0.4rem 0.35rem 0.8rem;
  border: 1px solid var(--border);
  border-radius: 12px;
  color: var(--muted);
  background: var(--surface);
}

.search-box:focus-within {
  border-color: rgba(124, 157, 255, 0.48);
  box-shadow: 0 0 0 3px rgba(124, 157, 255, 0.08);
}

.search-box input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  color: var(--text);
  background: transparent;
  font: inherit;
  font-size: 0.78rem;
}

.search-box input::placeholder { color: #56617b; }
.search-box input::-webkit-search-cancel-button { filter: invert(1); opacity: 0.4; }

.user-count {
  display: flex;
  align-items: baseline;
  gap: 0.3rem;
  padding: 0 0.4rem;
}

.user-count strong { color: var(--text); font-size: 1.1rem; font-weight: 700; }
.user-count span { color: var(--muted); font-size: 0.66rem; }

.user-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.7rem;
  margin-top: 0.9rem;
}

.user-card {
  padding: 1rem;
  border: 1px solid var(--border);
  border-radius: 15px;
  background: var(--surface);
  transition: border-color 0.18s ease, transform 0.18s ease;
}

.user-card:hover { border-color: rgba(133, 155, 255, 0.18); transform: translateY(-1px); }
.user-card.is-self { border-color: rgba(124, 157, 255, 0.2); background: linear-gradient(110deg, rgba(87, 108, 222, 0.08), rgba(255, 255, 255, 0.018)); }

.user-card-head { display: flex; align-items: center; gap: 0.7rem; }
.user-card-avatar { display: grid; width: 40px; height: 40px; flex: none; place-items: center; border-radius: 12px; color: #cfd9ff; background: linear-gradient(145deg, #4d5ca9, #31396f); font-size: 0.72rem; font-weight: 750; }
.user-card-avatar.is-admin { color: #c8f7ed; background: linear-gradient(145deg, #1b8274, #1c4b55); }
.user-card-name { min-width: 0; flex: 1; }
.user-card-name > div { display: flex; align-items: center; gap: 0.4rem; }
.user-card-name h2 { overflow: hidden; color: var(--text-soft); font-size: 0.8rem; font-weight: 630; text-overflow: ellipsis; white-space: nowrap; }
.user-card-name p { overflow: hidden; margin-top: 0.15rem; color: #67738f; font-size: 0.63rem; text-overflow: ellipsis; white-space: nowrap; }
.self-tag { padding: 0.08rem 0.3rem; border-radius: 4px; color: var(--primary-soft); background: rgba(124, 157, 255, 0.1); font-size: 0.52rem; font-weight: 650; }

.status-pill { display: inline-flex; align-items: center; gap: 0.32rem; flex: none; padding: 0.24rem 0.45rem; border-radius: 999px; font-size: 0.58rem; font-weight: 620; }
.status-pill span { width: 5px; height: 5px; border-radius: 50%; }
.status-pill.is-online { color: var(--accent); background: rgba(66, 232, 197, 0.07); }
.status-pill.is-online span { background: var(--accent); box-shadow: 0 0 7px var(--accent); }
.status-pill.is-offline { color: #7b849c; background: rgba(255, 255, 255, 0.045); }
.status-pill.is-offline span { background: #7b849c; }

.user-email { display: flex; align-items: center; gap: 0.35rem; overflow: hidden; margin-top: 0.7rem; color: #8490aa; font-size: 0.67rem; text-overflow: ellipsis; white-space: nowrap; }
.user-facts { display: grid; grid-template-columns: 1fr 1fr; gap: 0.6rem; margin-top: 0.75rem; padding: 0.7rem 0; border-top: 1px solid rgba(255, 255, 255, 0.045); border-bottom: 1px solid rgba(255, 255, 255, 0.045); }
.user-facts span { display: grid; gap: 0.18rem; }
.user-facts small { color: #616c87; font-size: 0.58rem; }
.user-facts strong { color: #9ca8c2; font-size: 0.62rem; font-weight: 530; }

.user-controls { display: flex; align-items: flex-end; justify-content: space-between; gap: 0.7rem; margin-top: 0.75rem; }
.role-select { display: grid; gap: 0.3rem; }
.role-select > span { color: #616c87; font-size: 0.58rem; }
.role-select select { padding: 0.4rem 1.8rem 0.4rem 0.55rem; border: 1px solid var(--border); border-radius: 8px; outline: 0; color: #c3cce4; background: #12182c; font: inherit; font-size: 0.65rem; cursor: pointer; }
.role-select select:disabled { opacity: 0.55; cursor: not-allowed; }

.admin-loading { display: flex; min-height: 260px; align-items: center; justify-content: center; gap: 0.55rem; color: var(--muted); font-size: 0.75rem; }
.empty-state { display: grid; place-items: center; padding: 4rem 1rem; text-align: center; }
.empty-icon { display: grid; width: 58px; height: 58px; place-items: center; border-radius: 17px; color: var(--muted); background: rgba(255, 255, 255, 0.04); }
.empty-state h2 { margin-top: 0.85rem; color: var(--text-soft); font-size: 0.95rem; }
.empty-state p { margin-top: 0.25rem; color: var(--muted); font-size: 0.72rem; }
.alert-action { margin-left: auto; border: 0; color: inherit; background: transparent; font-size: 0.7rem; text-decoration: underline; cursor: pointer; }

.pagination { display: flex; align-items: center; justify-content: center; gap: 1rem; margin-top: 1rem; color: var(--muted); font-size: 0.67rem; }

@media (max-width: 1080px) {
  .user-grid { grid-template-columns: 1fr; }
}

@media (max-width: 620px) {
  .admin-toolbar { flex-wrap: wrap; }
  .search-box { flex-basis: 100%; }
  .user-controls { align-items: stretch; flex-direction: column; }
  .role-select select { width: 100%; }
}
</style>
