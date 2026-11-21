<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { api, ApiError } from '@/lib/api'
import { formatDate } from '@/lib/format'
import { showToast } from '@/lib/toast'
import type { AuditLog, PageResponse } from '@/types'

const query = ref('')
const loading = ref(true)
const error = ref('')
const result = ref<PageResponse<AuditLog>>({ content: [], page: 0, size: 30, totalElements: 0, totalPages: 0 })

async function load(page = 0) {
  loading.value = true
  error.value = ''
  try {
    result.value = await api.auditLogs(query.value.trim(), page, result.value.size)
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : '无法加载审查日志'
  } finally {
    loading.value = false
  }
}

function search() { void load(0) }
function copyAddress(address: string | null) {
  if (!address) return
  void navigator.clipboard?.writeText(address).then(() => showToast('来源地址已复制', 'success'))
}

onMounted(() => void load())
</script>

<template>
  <div class="page">
    <header class="page-heading">
      <div>
        <p class="page-kicker"><AppIcon name="history" :size="13" /> AUDIT TRAIL</p>
        <h1>审查日志</h1>
        <p>记录后台账户变更，保留操作者、目标账户、变更内容与来源地址。</p>
      </div>
    </header>

    <section class="admin-toolbar">
      <form class="search-box" @submit.prevent="search">
        <AppIcon name="search" :size="18" />
        <input v-model="query" type="search" placeholder="搜索操作者、目标账户或动作" autocomplete="off" />
        <button class="btn btn-secondary btn-small" type="submit" :disabled="loading">搜索</button>
      </form>
      <div class="user-count">
        <strong>{{ result.totalElements }}</strong>
        <span>条日志</span>
      </div>
      <button class="icon-button" type="button" aria-label="刷新审查日志" :disabled="loading" @click="load(result.page)">
        <AppIcon name="refresh" :size="18" />
      </button>
    </section>

    <div v-if="error" class="alert alert-error" role="alert"><AppIcon name="alert" :size="18" /> {{ error }}<button class="alert-action" type="button" @click="load(0)">重试</button></div>
    <div v-if="loading" class="admin-loading"><span class="spinner"></span>正在读取审查记录…</div>
    <div v-else-if="result.content.length === 0" class="empty-state panel"><span class="empty-icon"><AppIcon name="history" :size="28" /></span><h2>暂无审查记录</h2><p>后台账户变更会显示在这里。</p></div>

    <section v-else class="audit-feed" aria-label="审查日志列表">
      <article v-for="(log, index) in result.content" :key="index" class="audit-entry">
        <div class="audit-entry-time">
          <AppIcon name="clock" :size="15" />
          <time>{{ formatDate(log.occurredAt) }}</time>
        </div>
        <div class="audit-entry-marker" aria-hidden="true"><AppIcon name="edit" :size="16" /></div>
        <div class="audit-entry-body">
          <div class="audit-entry-head">
            <div class="audit-entry-title">
              <span class="action-pill"><AppIcon name="shield-cat" :size="13" />{{ log.actionLabel }}</span>
              <strong>{{ log.operatorName }}</strong>
              <span class="entry-word">操作了</span>
              <strong>{{ log.targetName || '未指定账户' }}</strong>
            </div>
          </div>
          <p class="audit-summary">{{ log.summary }}</p>
          <div class="audit-entry-meta">
            <span><AppIcon name="globe" :size="13" /> 来源地址</span>
            <button v-if="log.sourceAddress" class="ip-button" type="button" @click="copyAddress(log.sourceAddress)">{{ log.sourceAddress }}</button>
            <span v-else class="unknown-value">未知</span>
          </div>
        </div>
      </article>
    </section>

    <nav v-if="result.totalPages > 1" class="pagination" aria-label="审查日志分页">
      <button class="btn btn-ghost btn-small" type="button" :disabled="result.page === 0 || loading" @click="load(result.page - 1)"><AppIcon name="chevron-left" :size="16" /> 上一页</button>
      <span>第 {{ result.page + 1 }} / {{ result.totalPages }} 页</span>
      <button class="btn btn-ghost btn-small" type="button" :disabled="result.page + 1 >= result.totalPages || loading" @click="load(result.page + 1)">下一页 <AppIcon name="chevron-right" :size="16" /></button>
    </nav>
  </div>
</template>

<style scoped>
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

.audit-feed { position: relative; display: grid; gap: .8rem; }
.audit-feed::before { content: ''; position: absolute; top: 1rem; bottom: 1rem; left: 207px; width: 1px; background: linear-gradient(180deg, rgba(112, 226, 197, .4), rgba(112, 226, 197, .08)); }
.audit-entry { position: relative; display: grid; grid-template-columns: 185px 44px minmax(0, 1fr); min-height: 128px; border: 1px solid rgba(126, 143, 184, .16); background: linear-gradient(110deg, rgba(20, 30, 54, .82), rgba(12, 18, 35, .88)); }
.audit-entry-time { display: flex; align-items: flex-start; gap: .42rem; padding: 1.1rem 1rem; color: var(--muted); font-size: .66rem; line-height: 1.5; }
.audit-entry-time svg { flex: none; color: #85d9c1; margin-top: .08rem; }
.audit-entry-marker { z-index: 1; display: grid; place-items: center; width: 30px; height: 30px; margin: 1rem auto 0; border: 1px solid rgba(133, 217, 193, .5); border-radius: 50%; color: #a7ebd8; background: #16253b; box-shadow: 0 0 0 5px rgba(10, 16, 31, .8); }
.audit-entry-body { min-width: 0; padding: 1rem 1.15rem 1rem .35rem; }
.audit-entry-title { display: flex; align-items: center; flex-wrap: wrap; gap: .45rem; color: #e8edf8; font-size: .78rem; }
.audit-entry-title strong { font-weight: 750; }
.entry-word { color: var(--muted); }
.action-pill { display: inline-flex; align-items: center; gap: .32rem; padding: .3rem .55rem; border: 1px solid rgba(121, 219, 196, .24); border-radius: 999px; color: #9fe7d1; background: rgba(64, 177, 151, .09); font-size: .62rem; white-space: nowrap; }
.audit-summary { margin: .75rem 0 .8rem; color: #fff; font-size: .92rem; font-weight: 700; line-height: 1.55; }
.audit-entry-meta { display: flex; align-items: center; flex-wrap: wrap; gap: .45rem; color: var(--muted); font-size: .65rem; }
.audit-entry-meta span:first-child { display: inline-flex; align-items: center; gap: .28rem; }
.audit-entry-meta span:first-child svg { color: #88a9d7; }
.ip-button { padding: 0; border: 0; color: #b8c8e8; background: transparent; font: inherit; cursor: pointer; }
.ip-button:hover { color: #fff; }
.unknown-value { color: #66738d; }

@media (max-width: 760px) {
  .admin-toolbar { flex-wrap: wrap; }
  .search-box { flex-basis: 100%; }
  .audit-feed::before { left: 22px; top: 1.25rem; bottom: 1.25rem; }
  .audit-entry { grid-template-columns: 44px minmax(0, 1fr); }
  .audit-entry-time { grid-column: 2; grid-row: 1; padding: 1rem 1rem 0 .35rem; font-size: .62rem; }
  .audit-entry-time svg { display: none; }
  .audit-entry-marker { grid-column: 1; grid-row: 1 / span 2; margin: 1rem auto 0; }
  .audit-entry-body { grid-column: 2; grid-row: 2; padding: .55rem 1rem 1rem .35rem; }
  .audit-entry-title { gap: .35rem; }
  .audit-summary { margin-top: .6rem; font-size: .84rem; }
}
</style>
