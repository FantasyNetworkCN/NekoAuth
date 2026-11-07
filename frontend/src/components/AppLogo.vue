<script setup lang="ts">
import logoLockup from '@/assets/brand/logo-lockup.png'
import logoMark from '@/assets/brand/logo-mark.png'

withDefaults(defineProps<{ size?: number; showName?: boolean }>(), {
  size: 38,
  showName: true,
})
</script>

<template>
  <span class="brand-lockup">
    <img
      class="brand-logo"
      :class="{ 'is-mark': !showName }"
      :src="showName ? logoLockup : logoMark"
      :style="{ height: `${size}px` }"
      alt="梦幻网络 Fantasy Network"
    />
  </span>
</template>

<style scoped>
.brand-lockup {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  color: var(--ink);
}

.brand-logo {
  display: block;
  width: auto;
  max-width: 100%;
  object-fit: contain;
  filter: drop-shadow(0 8px 18px rgba(0, 0, 0, 0.4));
}

.brand-logo.is-mark {
  width: auto;
  border-radius: 22%;
}
</style>
