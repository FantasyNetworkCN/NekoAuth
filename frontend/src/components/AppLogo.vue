<script setup lang="ts">
withDefaults(defineProps<{ size?: number; showName?: boolean }>(), {
  size: 38,
  showName: true,
})
</script>

<template>
  <span class="brand-lockup">
    <span class="logo-mark" :style="{ width: `${size}px`, height: `${size}px` }" aria-hidden="true">
      <svg viewBox="0 0 64 64">
        <defs>
          <linearGradient id="brand-sky" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stop-color="#ffd0ea" />
            <stop offset=".45" stop-color="#f58ac8" />
            <stop offset="1" stop-color="#786bec" />
          </linearGradient>
        </defs>
        <path d="M10 27 7 10l14 8c3-3 7-5 11-5s8 2 11 5l14-8-3 17c2 4 3 8 3 12 0 12-11 19-25 19S7 51 7 39c0-4 1-8 3-12z" fill="url(#brand-sky)" stroke="#fff1fb" stroke-width="2" />
        <path d="M16 20l3 4-5 1zM48 20l-3 4 5 1z" fill="#fff1fb" />
        <ellipse cx="24" cy="36" rx="4" ry="6" fill="#412055" />
        <ellipse cx="40" cy="36" rx="4" ry="6" fill="#412055" />
        <circle cx="22.5" cy="34" r="1.6" fill="#fff" />
        <circle cx="38.5" cy="34" r="1.6" fill="#fff" />
        <path d="M28 46c3 3 5 3 8 0" fill="none" stroke="#7a315e" stroke-width="2" stroke-linecap="round" />
        <path d="M19 42l-8-2M19 45l-8 3M45 42l8-2M45 45l8 3" stroke="#fff1fb" stroke-width="1.5" stroke-linecap="round" opacity=".8" />
      </svg>
    </span>
    <span v-if="showName" class="brand-copy">
      <span class="brand-name">Fantasy<span>Network</span></span>
      <small>梦幻网络 · Neko ID</small>
    </span>
  </span>
</template>

<style scoped>
.brand-lockup {
  display: inline-flex;
  align-items: center;
  gap: 0.72rem;
  color: var(--text);
}

.logo-mark {
  position: relative;
  display: grid;
  flex: none;
  place-items: center;
  border: 1px solid rgba(255, 213, 242, 0.68);
  border-radius: 16px 16px 13px 13px;
  background: linear-gradient(145deg, #fff4fc, #f1c8ff 42%, #8f7ef0);
  box-shadow: 0 8px 28px rgba(244, 108, 190, 0.34), inset 0 1px 0 rgba(255, 255, 255, 0.75);
  transform: rotate(-3deg);
}

.logo-mark::after {
  position: absolute;
  right: -3px;
  bottom: -3px;
  width: 9px;
  height: 9px;
  border: 2px solid var(--bg);
  border-radius: 999px;
  background: #77f1e0;
  box-shadow: 0 0 10px rgba(119, 241, 224, 0.9);
  content: '';
}

.logo-mark svg {
  width: 82%;
  height: 82%;
  filter: drop-shadow(0 2px 3px rgba(89, 35, 116, 0.14));
}

.brand-copy {
  display: grid;
  line-height: 1;
}

.brand-name {
  color: #fff6fd;
  font-family: 'Avenir Next', 'Trebuchet MS', 'PingFang SC', sans-serif;
  font-size: 1.12rem;
  font-weight: 850;
  letter-spacing: -0.045em;
}

.brand-name span {
  color: #ff9ed6;
}

.brand-copy small {
  margin-top: 0.28rem;
  color: #b7a6d8;
  font-size: 0.52rem;
  font-weight: 650;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}
</style>
