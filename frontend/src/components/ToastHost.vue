<script setup lang="ts">
import AppIcon from './AppIcon.vue'
import { dismissToast, toasts } from '@/lib/toast'
</script>

<template>
  <Teleport to="body">
    <div class="toast-stack" aria-live="polite">
      <TransitionGroup name="toast">
        <div v-for="toast in toasts" :key="toast.id" class="toast" :class="`toast-${toast.kind}`">
          <span class="toast-icon">
            <AppIcon :name="toast.kind === 'success' ? 'check' : toast.kind === 'error' ? 'alert' : 'spark'" :size="17" />
          </span>
          <span class="toast-message">{{ toast.message }}</span>
          <button class="toast-close" type="button" aria-label="关闭提示" @click="dismissToast(toast.id)">
            <AppIcon name="close" :size="15" />
          </button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
.toast-stack {
  position: fixed;
  z-index: 1000;
  right: 1.25rem;
  bottom: 1.25rem;
  display: grid;
  width: min(380px, calc(100vw - 2rem));
  gap: 0.65rem;
  pointer-events: none;
}

.toast {
  display: flex;
  align-items: flex-start;
  gap: 0.65rem;
  padding: 0.8rem 0.85rem;
  border: 1px solid var(--border-strong);
  border-radius: 13px;
  background: rgba(16, 22, 41, 0.96);
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.4);
  color: var(--text);
  pointer-events: auto;
  backdrop-filter: blur(18px);
}

.toast-icon {
  display: grid;
  flex: none;
  width: 28px;
  height: 28px;
  place-items: center;
  border-radius: 9px;
  color: var(--primary-soft);
  background: rgba(124, 157, 255, 0.12);
}

.toast-success .toast-icon {
  color: var(--accent);
  background: rgba(66, 232, 197, 0.1);
}

.toast-error .toast-icon {
  color: var(--danger);
  background: rgba(255, 107, 129, 0.1);
}

.toast-message {
  flex: 1;
  padding-top: 0.2rem;
  font-size: 0.875rem;
  line-height: 1.45;
}

.toast-close {
  display: grid;
  width: 26px;
  height: 26px;
  flex: none;
  place-items: center;
  border: 0;
  border-radius: 7px;
  color: var(--muted);
  background: transparent;
  cursor: pointer;
}

.toast-close:hover {
  color: var(--text);
  background: rgba(255, 255, 255, 0.06);
}

.toast-enter-active,
.toast-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>
