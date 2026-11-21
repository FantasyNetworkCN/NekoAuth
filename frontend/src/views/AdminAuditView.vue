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
function actionLabel(action: string) { return action === 'USER_UPDATED' ? '更新用户账户' : action }
function copyIp(ip: string | null) {
  if (!ip) return
  void navigator.clipboard?.writeText(ip).then(() => showToast('IP 地址已复制', 'success'))
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
      <strong class="audit-total">{{ result.totalElements }} 条记录</strong>
    </header>

    <section class="admin-toolbar">
      <form class="search-box" @submit.prevent="search">
        <AppIcon name="search" :size="18" />
        <input v-model="query" type="search" placeholder="搜索操作者、目标账户或动作" autocomplete="off" />
        <button class="btn btn-secondary btn-small" type="submit" :disabled="loading">搜索</button>
      </form>
      <button class="icon-button" type="button" aria-label="刷新审查日志" :disabled="loading" @click="load(result.page)">
        <AppIcon name="refresh" :size="18" />
      </button>
    </section>

    <div v-if="error" class="alert alert-error" role="alert"><AppIcon name="alert" :size="18" /> {{ error }}<button class="alert-action" type="button" @click="load(0)">重试</button></div>
    <div v-if="loading" class="admin-loading"><span class="spinner"></span>正在读取审查记录…</div>
    <div v-else-if="result.content.length === 0" class="empty-state panel"><span class="empty-icon"><AppIcon name="history" :size="28" /></span><h2>暂无审查记录</h2><p>后台账户变更会显示在这里。</p></div>

    <section v-else class="audit-table-wrap" aria-label="审查日志列表">
      <table class="audit-table">
        <thead><tr><th>时间</th><th>操作者</th><th>动作</th><th>目标账户</th><th>变更内容</th><th>来源 IP</th></tr></thead>
        <tbody>
          <tr v-for="log in result.content" :key="log.id">
            <td class="muted-cell">{{ formatDate(log.createdAt) }}</td>
            <td><strong>{{ log.actorUsername }}</strong><small>#{{ log.actorId }}</small></td>
            <td><span class="action-pill">{{ actionLabel(log.action) }}</span></td>
            <td>{{ log.targetUsername || '系统' }}<small v-if="log.targetId">#{{ log.targetId }}</small></td>
            <td class="details-cell">{{ log.details }}</td>
            <td><button v-if="log.ipAddress" class="ip-button" type="button" @click="copyIp(log.ipAddress)">{{ log.ipAddress }}</button><span v-else>未知</span></td>
          </tr>
        </tbody>
      </table>
    </section>

    <nav v-if="result.totalPages > 1" class="pagination" aria-label="审查日志分页">
      <button class="btn btn-ghost btn-small" type="button" :disabled="result.page === 0 || loading" @click="load(result.page - 1)"><AppIcon name="chevron-left" :size="16" /> 上一页</button>
      <span>第 {{ result.page + 1 }} / {{ result.totalPages }} 页</span>
      <button class="btn btn-ghost btn-small" type="button" :disabled="result.page + 1 >= result.totalPages || loading" @click="load(result.page + 1)">下一页 <AppIcon name="chevron-right" :size="16" /></button>
    </nav>
  </div>
</template>

<style scoped>
.audit-total { color: var(--muted); font-size: .75rem; font-weight: 700; }
.audit-table-wrap { overflow-x: auto; border: 1px solid var(--border); background: rgba(10, 15, 30, .56); }
.audit-table { width: 100%; border-collapse: collapse; min-width: 860px; font-size: .72rem; }
.audit-table th, .audit-table td { padding: .85rem .9rem; border-bottom: 1px solid rgba(126, 143, 184, .12); text-align: left; vertical-align: top; }
.audit-table th { color: var(--muted); font-size: .62rem; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; }
.audit-table td strong, .audit-table td small { display: block; }
.audit-table td small { margin-top: .18rem; color: var(--muted); font-size: .62rem; }
.muted-cell { color: var(--muted); white-space: nowrap; }
.details-cell { color: #c7d0e5; min-width: 260px; }
.action-pill, .protected-label { display: inline-flex; align-items: center; padding: .28rem .5rem; border: 1px solid rgba(121, 219, 196, .24); border-radius: 999px; color: #9fe7d1; background: rgba(64, 177, 151, .09); font-size: .62rem; white-space: nowrap; }
.ip-button { padding: 0; border: 0; color: #b8c8e8; background: transparent; font: inherit; cursor: pointer; }
.ip-button:hover { color: #fff; }
</style>
