<script setup lang="ts">
import AppLogo from './AppLogo.vue'
import heroNeko from '@/assets/local/hero-neko.webp'

defineProps<{ eyebrow: string; title: string; subtitle: string }>()
</script>

<template>
  <div class="auth-page">
    <div class="auth-shell">
      <section class="auth-art">
        <img class="auth-art-image" :src="heroNeko" alt="梦幻网络猫娘角色插画" />
        <div class="art-shade" aria-hidden="true"></div>
        <div class="art-grain" aria-hidden="true"></div>

        <header class="art-brand">
          <AppLogo :size="42" />
          <span>NEKO IDENTITY</span>
        </header>

        <div class="art-copy">
          <p class="art-eyebrow">/ ONE ID · EVERY WORLD</p>
          <h1>一个身份，<br />连接所有<span>数字世界。</span></h1>
          <p>登录 Neko ID，管理密码与设备，也可以把身份安全地交给已接入的 OAuth 应用。</p>
        </div>

        <div class="art-footer">
          <span><i></i> ALL SYSTEMS ONLINE</span>
          <span>OAuth 2.0 · PKCE · OIDC</span>
        </div>
      </section>

      <main class="auth-form-side">
        <div class="form-glow" aria-hidden="true"></div>
        <div class="auth-form-wrap">
          <div class="mobile-brand"><AppLogo :size="38" /></div>

          <div class="auth-heading">
            <p class="auth-eyebrow"><span></span>{{ eyebrow }}</p>
            <h2>{{ title }}</h2>
            <p class="auth-subtitle">{{ subtitle }}</p>
          </div>

          <div class="form-card">
            <slot />
          </div>

          <p class="auth-legal">登录即表示你同意《梦幻网络服务条款》与《隐私政策》</p>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  display: grid;
  min-height: 100svh;
  place-items: stretch;
  padding: 0;
  background:
    radial-gradient(circle at 86% 4%, rgba(255, 157, 122, 0.09), transparent 30%),
    radial-gradient(circle at 8% 88%, rgba(232, 189, 102, 0.055), transparent 34%),
    var(--bg);
}

.auth-shell {
  display: grid;
  width: 100%;
  height: 100svh;
  min-height: 0;
  grid-template-columns: minmax(0, 0.94fr) minmax(460px, 1.06fr);
  overflow: hidden;
  border: 0;
  border-radius: 0;
  background: #130e0b;
}

.auth-art {
  position: relative;
  isolation: isolate;
  display: flex;
  min-height: 100%;
  overflow: hidden;
  flex-direction: column;
  justify-content: space-between;
  padding: clamp(1.3rem, 2.8vw, 2.2rem);
  background: #1a120e;
}

.auth-art-image {
  position: absolute;
  z-index: -3;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: 50% 28%;
  filter: saturate(0.92) contrast(1.02) sepia(0.05);
  transform: scale(1.015);
}

.art-shade {
  position: absolute;
  z-index: -2;
  inset: 0;
  background:
    linear-gradient(180deg, rgba(16, 12, 10, 0.28), transparent 32% 48%, rgba(16, 12, 10, 0.94)),
    linear-gradient(90deg, rgba(16, 12, 10, 0.38), transparent 58%);
  pointer-events: none;
}

.art-grain {
  position: absolute;
  z-index: -1;
  inset: 0;
  opacity: 0.16;
  background-image:
    linear-gradient(rgba(255, 235, 215, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 235, 215, 0.08) 1px, transparent 1px);
  background-size: 92px 92px;
  mask-image: linear-gradient(180deg, black, transparent 72%);
  pointer-events: none;
}

.art-brand,
.art-copy,
.art-footer {
  position: relative;
  z-index: 1;
}

.art-brand {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.art-brand > span {
  display: inline-flex;
  min-height: 32px;
  align-items: center;
  padding: 0 0.72rem;
  border: 1px solid rgba(255, 235, 215, 0.2);
  border-radius: var(--r-pill);
  color: var(--body);
  background: rgba(20, 14, 11, 0.52);
  font-family: var(--font-mono);
  font-size: 0.5rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  backdrop-filter: blur(14px);
}

.art-copy {
  max-width: 510px;
  padding-top: clamp(7.5rem, 21vh, 13rem);
}

.art-eyebrow {
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 0.58rem;
  font-weight: 700;
  letter-spacing: 0.15em;
}

.art-copy h1 {
  margin-top: 0.85rem;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: clamp(2.5rem, 4.6vw, 4.6rem);
  font-weight: 830;
  letter-spacing: -0.06em;
  line-height: 1.04;
  text-wrap: balance;
}

.art-copy h1 span {
  color: transparent;
  background: var(--gradient-text);
  background-clip: text;
  -webkit-background-clip: text;
}

.art-copy > p:last-child {
  max-width: 430px;
  margin-top: 1rem;
  color: rgba(251, 243, 231, 0.76);
  font-size: 0.75rem;
  line-height: 1.8;
}

.art-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding-top: 0.9rem;
  border-top: 1px solid rgba(255, 235, 215, 0.13);
  color: rgba(251, 243, 231, 0.56);
  font-family: var(--font-mono);
  font-size: 0.47rem;
  font-weight: 650;
  letter-spacing: 0.08em;
}

.art-footer span:first-child {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
}

.art-footer i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--success);
  box-shadow: 0 0 9px rgba(157, 212, 175, 0.58);
}

.auth-form-side {
  position: relative;
  display: flex;
  height: 100%;
  min-height: 0;
  align-items: safe center;
  justify-content: center;
  overflow-x: hidden;
  overflow-y: auto;
  padding: clamp(2rem, 4.4vw, 5.25rem);
  background:
    radial-gradient(circle at 100% 0%, rgba(255, 157, 122, 0.08), transparent 28%),
    linear-gradient(180deg, #17110e, #100c0a);
}

.auth-form-side::before {
  position: absolute;
  inset: 0;
  opacity: 0.42;
  background-image:
    linear-gradient(var(--line-soft) 1px, transparent 1px),
    linear-gradient(90deg, var(--line-soft) 1px, transparent 1px);
  background-size: 64px 64px;
  content: '';
  mask-image: radial-gradient(ellipse at 50% 34%, black, transparent 80%);
  pointer-events: none;
}

.form-glow {
  position: absolute;
  right: -26%;
  bottom: -22%;
  width: 430px;
  height: 430px;
  border-radius: 50%;
  background: var(--accent);
  filter: blur(130px);
  opacity: 0.065;
  pointer-events: none;
}

.auth-form-wrap {
  position: relative;
  z-index: 1;
  width: min(440px, 100%);
  animation: form-in 0.6s var(--ease-out) both;
}

.mobile-brand {
  display: none;
  margin-bottom: 1.2rem;
}

.auth-heading {
  margin-bottom: 1.25rem;
}

.auth-eyebrow {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.62rem;
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 0.56rem;
  font-weight: 700;
  letter-spacing: 0.15em;
  text-transform: uppercase;
}

.auth-eyebrow span {
  width: 22px;
  height: 1px;
  background: linear-gradient(90deg, var(--accent), transparent);
}

.auth-heading h2 {
  color: var(--ink);
  font-family: var(--font-display);
  font-size: clamp(1.85rem, 3vw, 2.45rem);
  font-weight: 810;
  letter-spacing: -0.055em;
  line-height: 1.08;
  text-wrap: balance;
}

.auth-subtitle {
  max-width: 390px;
  margin-top: 0.65rem;
  color: var(--body);
  font-size: 0.73rem;
  line-height: 1.75;
}

.form-card {
  position: relative;
  overflow: hidden;
  padding: clamp(1rem, 2.5vw, 1.4rem);
  border: 1px solid var(--line);
  border-radius: 24px;
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.04), transparent 46%),
    rgba(32, 24, 20, 0.8);
  box-shadow:
    0 32px 84px -40px rgba(0, 0, 0, 0.9),
    inset 0 1px 0 rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(22px) saturate(115%);
}

.form-card::before {
  position: absolute;
  inset: 0 1.2rem auto;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255, 157, 122, 0.5), transparent);
  content: '';
}

.auth-legal {
  margin-top: 1rem;
  color: var(--muted);
  font-size: 0.53rem;
  line-height: 1.6;
  text-align: center;
}

@keyframes form-in {
  from { opacity: 0; transform: translateY(16px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (min-width: 1600px) {
  .auth-shell {
    grid-template-columns: minmax(0, 0.98fr) minmax(500px, 1.02fr);
  }

  .auth-art {
    padding: 3rem 4.2rem;
  }

  .art-copy {
    max-width: 590px;
  }

  .art-copy h1 {
    font-size: clamp(4.2rem, 4.8vw, 5.7rem);
  }

  .auth-form-side {
    padding-inline: clamp(4rem, 8vw, 10rem);
  }

  .auth-form-wrap {
    width: min(480px, 100%);
  }

  .form-card {
    padding: 1.75rem;
  }
}

@media (max-width: 920px) {
  .auth-page {
    display: block;
    min-height: 100svh;
    padding: 0;
  }

  .auth-shell {
    display: block;
    height: auto;
    min-height: 100svh;
    border: 0;
    border-radius: 0;
  }

  .auth-art {
    min-height: 210px;
    padding: 1rem 1.15rem;
  }

  .auth-art-image {
    object-position: 50% 26%;
  }

  .art-copy {
    max-width: 520px;
    padding-top: 3.8rem;
  }

  .art-copy h1 {
    font-size: clamp(1.7rem, 7vw, 2.5rem);
  }

  .art-copy > p:last-child,
  .art-footer {
    display: none;
  }

  .auth-form-side {
    min-height: calc(100svh - 210px);
    align-items: flex-start;
    padding: 1.5rem 1rem 2rem;
  }

  .mobile-brand {
    display: block;
  }
}

@media (max-width: 560px) {
  .auth-art {
    min-height: 188px;
  }

  .art-brand > span {
    display: none;
  }

  .art-copy {
    padding-top: 2.8rem;
  }

  .art-eyebrow {
    font-size: 0.5rem;
  }

  .auth-form-side {
    min-height: calc(100svh - 188px);
  }

  .form-card {
    padding: 1rem;
    border-radius: 20px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .auth-form-wrap {
    animation: none;
  }
}
</style>
