<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AuthLayout from '@/components/AuthLayout.vue'
import AppIcon from '@/components/AppIcon.vue'

const route = useRoute()
const router = useRouter()

function query(name: string): string {
  const value = route.query[name]
  if (typeof value === 'string') return value
  if (Array.isArray(value)) return value[0] || ''
  return ''
}

const reason = computed(() => query('reason'))
const hasHint = computed(() => query('id_token_hint').length > 0)
const notice = computed(() => {
  if (reason.value === 'session_mismatch') {
    return '这个登出请求来自另一个账户，或与当前浏览器的登录会话不一致。请确认是否仍要退出当前浏览器会话。'
  }
  if (reason.value === 'no_session') {
    return '当前浏览器没有有效的登录会话。请确认这是你发起的登出请求；确认后不会影响其他账户。'
  }
  return '应用没有提供经过签名的 ID Token 作为登出凭证，因此需要你手动确认这次登出。'
})

function cancel() {
  if (window.history.length > 1) {
    router.back()
    return
  }
  void router.replace({ name: 'account' })
}
</script>

<template>
  <AuthLayout
    eyebrow="Session Guardian"
    title="要结束这段会话吗？"
    subtitle="小梦会先确认请求来源。只有你亲自按下按钮，才会清理当前浏览器会话。"
  >
    <div class="logout-confirm">
      <div class="logout-icon"><AppIcon name="logout" :size="24" /></div>
      <p class="logout-notice">{{ notice }}</p>
      <div class="logout-boundary">
        <AppIcon name="shield" :size="16" />
        <span>{{ hasHint ? '已收到签名 ID Token，但无法与当前浏览器会话自动匹配。' : '本次登出请求没有 ID Token 回执。' }}</span>
      </div>

      <form method="post" action="/oauth2/logout/confirm" class="logout-actions">
        <input v-if="query('id_token_hint')" type="hidden" name="id_token_hint" :value="query('id_token_hint')" />
        <input v-if="query('client_id')" type="hidden" name="client_id" :value="query('client_id')" />
        <input v-if="query('post_logout_redirect_uri')" type="hidden" name="post_logout_redirect_uri" :value="query('post_logout_redirect_uri')" />
        <input v-if="query('state')" type="hidden" name="state" :value="query('state')" />
        <button class="btn btn-secondary btn-block" type="button" @click="cancel">取消，保持登录</button>
        <button class="btn btn-primary btn-block" type="submit">
          <AppIcon name="logout" :size="17" />
          确认退出
        </button>
      </form>
    </div>
  </AuthLayout>
</template>

<style scoped>
.logout-confirm {
  display: grid;
  gap: 1rem;
}

.logout-icon {
  display: grid;
  width: 54px;
  height: 54px;
  place-items: center;
  border: 1px solid rgba(255, 183, 226, 0.22);
  border-radius: 18px;
  color: #ffc5e8;
  background: rgba(255, 198, 234, 0.08);
}

.logout-notice {
  margin: 0;
  color: #c8b8d7;
  font-size: 0.74rem;
  line-height: 1.8;
}

.logout-boundary {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 0.7rem 0.8rem;
  border: 1px solid rgba(255, 194, 232, 0.12);
  border-radius: 14px;
  color: #9d8aaf;
  background: rgba(255, 226, 247, 0.035);
  font-size: 0.64rem;
  line-height: 1.6;
}

.logout-actions {
  display: grid;
  gap: 0.65rem;
  margin-top: 0.2rem;
}
</style>
