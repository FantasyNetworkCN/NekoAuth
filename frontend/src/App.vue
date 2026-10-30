<script setup lang="ts">
import { RouterView } from 'vue-router'
import ToastHost from './components/ToastHost.vue'
import NekoMascot from './components/NekoMascot.vue'
</script>

<template>
  <div class="ambient-paws" aria-hidden="true">
    <span>ฅ</span><span>✧</span><span>🐾</span><span>♡</span><span>ฅ</span>
  </div>
  <NekoMascot class="corner-neko" pose="sleep" compact />
  <RouterView />
  <ToastHost />
</template>

<style scoped>
.corner-neko {
  position: fixed;
  z-index: 0;
  bottom: -54px;
  left: -26px;
  width: 160px;
  opacity: 0.12;
  pointer-events: none;
  filter: saturate(0.8) blur(0.2px);
}

@media (max-width: 900px) {
  .corner-neko { display: none; }
}
</style>
