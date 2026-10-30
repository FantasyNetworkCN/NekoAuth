<script setup lang="ts">
import { RouterLink } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import AppLogo from '@/components/AppLogo.vue'
import NekoMascot from '@/components/NekoMascot.vue'
import { auth } from '@/stores/auth'
</script>

<template>
  <main class="not-found-page">
    <div class="not-found-card">
      <AppLogo :size="44" />
      <div class="lost-neko"><NekoMascot pose="sleep" compact /></div>
      <p class="not-found-code">404 · 迷路的猫爪印</p>
      <h1>这条世界线不存在喵</h1>
      <p class="not-found-copy">小梦找遍了整片梦域，也没有找到这个地址。也许是传送阵画错了？</p>
      <RouterLink class="btn btn-primary" :to="auth.isAdmin.value ? '/admin' : '/login'">
        <AppIcon :name="auth.isAdmin.value ? 'shield-cat' : 'paw'" :size="17" />
        {{ auth.isAdmin.value ? '回到猫屋' : '回到猫娘身边' }}
      </RouterLink>
    </div>
  </main>
</template>

<style scoped>
.not-found-page {
  display: grid;
  min-height: 100svh;
  padding: 2rem;
  place-items: center;
  background:
    radial-gradient(circle at 50% 0%, rgba(246, 141, 202, 0.14), transparent 38%),
    radial-gradient(circle at 10% 100%, rgba(94, 231, 214, 0.07), transparent 32%),
    var(--bg);
}

.not-found-card {
  display: grid;
  width: min(450px, 100%);
  justify-items: center;
  padding: clamp(1.6rem, 6vw, 3rem);
  border: 1px solid rgba(255, 203, 239, 0.13);
  border-radius: 24px;
  background: linear-gradient(145deg, rgba(255, 231, 249, 0.045), transparent 45%), rgba(25, 16, 42, 0.88);
  box-shadow: 0 30px 90px rgba(0, 0, 0, 0.36), inset 0 1px 0 rgba(255, 255, 255, 0.06);
  text-align: center;
  backdrop-filter: blur(18px);
}

.lost-neko {
  display: grid;
  width: 150px;
  height: 120px;
  justify-items: center;
  margin-top: -0.1rem;
  overflow: hidden;
}

.lost-neko :deep(.neko-mascot) { width: 135px; }

.not-found-code {
  margin-top: 0.15rem;
  color: #ffadd8;
  font-size: 0.62rem;
  font-weight: 800;
  letter-spacing: 0.13em;
}

h1 {
  margin-top: 0.4rem;
  color: #fff4fc;
  font-size: clamp(1.5rem, 5vw, 2rem);
  font-weight: 800;
  letter-spacing: -0.05em;
}

.not-found-copy {
  max-width: 330px;
  margin: 0.7rem 0 1.5rem;
  color: #907c9f;
  font-size: 0.7rem;
  line-height: 1.7;
}
</style>
