<script setup lang="ts">
import AppIcon from './AppIcon.vue'
import AppLogo from './AppLogo.vue'
import NekoMascot from './NekoMascot.vue'

defineProps<{ eyebrow: string; title: string; subtitle: string }>()
</script>

<template>
  <div class="auth-page">
    <section class="auth-visual">
      <div class="visual-stars" aria-hidden="true">
        <span>✧</span><span>✦</span><span>♡</span><span>✧</span><span>🐾</span><span>✦</span>
      </div>
      <div class="visual-halftone" aria-hidden="true"></div>
      <div class="jp-rail" aria-hidden="true">夢を紡ぐ、猫と共に</div>

      <div class="visual-top">
        <AppLogo :size="42" />
        <span class="visual-pill"><span class="status-dot"></span> NEKO IDENTITY</span>
      </div>

      <div class="visual-stage">
        <div class="mascot-halo" aria-hidden="true"></div>
        <NekoMascot class="visual-mascot" pose="greet" />
        <div class="speech-bubble">
          <span>欢迎回家，主人～</span>
          <small>今天也由小梦守护你的身份喵♡</small>
        </div>
        <span class="sticker sticker-pkce">PKCE<br /><strong>S256</strong></span>
        <span class="sticker sticker-oidc">OIDC<br /><strong>READY</strong></span>
        <span class="floating-paw paw-one" aria-hidden="true">🐾</span>
        <span class="floating-paw paw-two" aria-hidden="true">ฅ</span>
      </div>

      <div class="visual-copy">
        <p class="kicker"><AppIcon name="spark" :size="16" /> FANTASY NETWORK · AUTH GARDEN</p>
        <h1>连接每一个<br /><span>梦幻世界。</span></h1>
        <p class="visual-lead">
          一套身份，畅游整个幻想网络。猫娘向导会用 OAuth 2.0、PKCE 与 OIDC，
          把你的登录令牌稳稳收进项圈里。
        </p>
      </div>

      <div class="visual-features">
        <div class="feature-chip">
          <span class="feature-icon"><AppIcon name="shield-cat" :size="18" /></span>
          <span><strong>猫娘级守护</strong><small>短令牌 + HttpOnly</small></span>
        </div>
        <div class="feature-chip">
          <span class="feature-icon"><AppIcon name="key" :size="18" /></span>
          <span><strong>OAuth 2.0 + PKCE</strong><small>标准授权码流程</small></span>
        </div>
        <div class="feature-chip">
          <span class="feature-icon"><AppIcon name="spark" :size="18" /></span>
          <span><strong>OIDC 身份</strong><small>发现 · JWKS · UserInfo</small></span>
        </div>
      </div>

      <p class="visual-footnote">FantasyNetwork 梦幻网络 · 让身份像星光一样可信</p>
    </section>

    <main class="auth-form-side">
      <div class="form-sparkles" aria-hidden="true"><span>✧</span><span>♡</span><span>✦</span></div>
      <div class="auth-form-wrap">
        <div class="mobile-brand"><AppLogo :size="36" /></div>
        <div class="auth-heading">
          <p class="auth-eyebrow"><span></span>{{ eyebrow }}</p>
          <h2>{{ title }}</h2>
          <p class="auth-subtitle">{{ subtitle }}</p>
        </div>
        <div class="form-card">
          <slot />
        </div>
        <p class="auth-legal">
          <AppIcon name="paw" :size="13" /> 登录即表示你同意《梦幻网络服务条款》与《隐私政策》。
        </p>
      </div>
    </main>
  </div>
</template>

<style scoped>
.auth-page {
  display: grid;
  min-height: 100svh;
  grid-template-columns: minmax(440px, 1.02fr) minmax(470px, 0.98fr);
  background: #120d24;
}

.auth-visual {
  position: relative;
  display: flex;
  overflow: hidden;
  flex-direction: column;
  justify-content: space-between;
  padding: clamp(1.6rem, 3.4vw, 3rem);
  border-right: 1px solid rgba(255, 196, 234, 0.14);
  background:
    radial-gradient(circle at 73% 35%, rgba(255, 169, 221, 0.38), transparent 25%),
    radial-gradient(circle at 19% 82%, rgba(101, 232, 225, 0.16), transparent 33%),
    radial-gradient(circle at 98% 4%, rgba(126, 102, 229, 0.46), transparent 33%),
    linear-gradient(148deg, #201334 0%, #271844 48%, #17102c 100%);
}

.visual-stars {
  position: absolute;
  inset: 0;
  color: rgba(255, 239, 251, 0.75);
  font-size: 1rem;
  pointer-events: none;
}

.visual-stars span {
  position: absolute;
  animation: twinkle 3.4s ease-in-out infinite alternate;
}

.visual-stars span:nth-child(1) { top: 9%; left: 44%; font-size: 1.5rem; }
.visual-stars span:nth-child(2) { top: 24%; right: 8%; animation-delay: -1.2s; }
.visual-stars span:nth-child(3) { bottom: 28%; left: 7%; color: #ffa9d9; font-size: 1.4rem; animation-delay: -2.1s; }
.visual-stars span:nth-child(4) { top: 38%; left: 50%; color: #a8fff1; animation-delay: -0.7s; }
.visual-stars span:nth-child(5) { bottom: 16%; right: 17%; filter: grayscale(0.2); animation-delay: -2.6s; }
.visual-stars span:nth-child(6) { top: 15%; left: 12%; color: #ffd37a; animation-delay: -1.8s; }

.visual-halftone {
  position: absolute;
  inset: 0;
  opacity: 0.17;
  background-image: radial-gradient(rgba(255, 255, 255, 0.8) 1px, transparent 1.5px);
  background-size: 17px 17px;
  mask-image: linear-gradient(135deg, transparent 20%, black 70%, transparent 100%);
  pointer-events: none;
}

.jp-rail {
  position: absolute;
  top: 27%;
  right: 1.3rem;
  color: rgba(255, 226, 246, 0.28);
  font-family: 'Songti SC', 'Noto Serif SC', serif;
  font-size: 0.7rem;
  letter-spacing: 0.42em;
  text-transform: uppercase;
  writing-mode: vertical-rl;
}

.visual-top,
.visual-copy,
.visual-features,
.visual-footnote {
  position: relative;
  z-index: 2;
}

.visual-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.visual-pill {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.44rem 0.68rem;
  border: 1px solid rgba(255, 195, 234, 0.2);
  border-radius: 999px;
  color: #f8cdea;
  background: rgba(255, 218, 246, 0.07);
  font-size: 0.55rem;
  font-weight: 800;
  letter-spacing: 0.13em;
  backdrop-filter: blur(10px);
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: #7cf4df;
  box-shadow: 0 0 10px #7cf4df;
}

.visual-stage {
  position: relative;
  display: grid;
  min-height: 330px;
  flex: 1;
  place-items: center;
  margin: -0.4rem 0 -0.8rem;
}

.mascot-halo {
  position: absolute;
  width: min(78%, 390px);
  aspect-ratio: 1;
  border: 1px solid rgba(255, 211, 241, 0.24);
  border-radius: 50%;
  background:
    radial-gradient(circle, rgba(255, 223, 246, 0.21), rgba(255, 180, 228, 0.04) 42%, transparent 68%);
  box-shadow: inset 0 0 60px rgba(255, 195, 237, 0.08), 0 0 80px rgba(219, 133, 255, 0.12);
}

.mascot-halo::before,
.mascot-halo::after {
  position: absolute;
  inset: 10%;
  border: 1px dashed rgba(255, 217, 245, 0.19);
  border-radius: 50%;
  content: '';
}

.mascot-halo::after {
  inset: 23%;
  border-style: solid;
  opacity: 0.6;
}

.visual-mascot {
  position: relative;
  z-index: 1;
  width: min(85%, 390px);
  height: auto;
  max-height: 410px;
}

.speech-bubble {
  position: absolute;
  z-index: 3;
  top: 17%;
  left: 0;
  display: grid;
  gap: 0.22rem;
  max-width: 180px;
  padding: 0.72rem 0.9rem;
  border: 2px solid rgba(255, 230, 248, 0.92);
  border-radius: 18px 18px 5px 18px;
  color: #6b3f70;
  background: linear-gradient(145deg, #fffafd, #ffe9f7);
  box-shadow: 0 14px 34px rgba(47, 15, 67, 0.26);
  transform: rotate(-3deg);
}

.speech-bubble::after {
  position: absolute;
  right: 20px;
  bottom: -10px;
  width: 16px;
  height: 16px;
  border-right: 2px solid rgba(255, 230, 248, 0.92);
  border-bottom: 2px solid rgba(255, 230, 248, 0.92);
  background: #ffe9f7;
  content: '';
  transform: rotate(45deg);
}

.speech-bubble span {
  font-size: 0.8rem;
  font-weight: 800;
}

.speech-bubble small {
  color: #9d6b9b;
  font-size: 0.61rem;
  line-height: 1.55;
}

.sticker {
  position: absolute;
  z-index: 3;
  display: grid;
  place-items: center;
  width: 72px;
  height: 72px;
  border: 1px solid rgba(255, 242, 252, 0.55);
  border-radius: 50%;
  color: #fff;
  background: rgba(255, 255, 255, 0.07);
  box-shadow: inset 0 0 30px rgba(255, 255, 255, 0.06), 0 10px 30px rgba(36, 12, 55, 0.18);
  font-size: 0.48rem;
  font-weight: 650;
  letter-spacing: 0.08em;
  line-height: 1.4;
  text-align: center;
  backdrop-filter: blur(8px);
}

.sticker strong {
  color: #9dfff2;
  font-size: 0.68rem;
}

.sticker-pkce {
  right: 3%;
  top: 36%;
  transform: rotate(7deg);
}

.sticker-oidc {
  right: 8%;
  bottom: 16%;
  transform: rotate(-6deg);
}

.floating-paw {
  position: absolute;
  z-index: 2;
  color: rgba(255, 224, 246, 0.5);
  font-size: 1.2rem;
}

.paw-one { left: 9%; bottom: 23%; transform: rotate(-20deg); }
.paw-two { right: 22%; top: 10%; font-size: 1.5rem; transform: rotate(14deg); }

.visual-copy {
  max-width: 560px;
}

.kicker {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  margin-bottom: 0.72rem;
  color: #ffb9e1;
  font-size: 0.56rem;
  font-weight: 800;
  letter-spacing: 0.16em;
}

.visual-copy h1 {
  color: #fff8fe;
  font-family: 'Avenir Next', 'PingFang SC', sans-serif;
  font-size: clamp(2.1rem, 3.8vw, 3.55rem);
  font-weight: 800;
  letter-spacing: -0.075em;
  line-height: 1.02;
}

.visual-copy h1 span {
  color: transparent;
  background: linear-gradient(100deg, #ffb8e2 4%, #fff3fc 43%, #9ef5e8 104%);
  background-clip: text;
  -webkit-background-clip: text;
}

.visual-lead {
  max-width: 470px;
  margin-top: 0.9rem;
  color: #c5b5db;
  font-size: 0.75rem;
  line-height: 1.8;
}

.visual-features {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.58rem;
  margin-top: 1.15rem;
}

.feature-chip {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
  padding: 0.6rem 0.55rem;
  border: 1px solid rgba(255, 207, 239, 0.11);
  border-radius: 12px;
  background: rgba(255, 239, 251, 0.045);
  backdrop-filter: blur(12px);
}

.feature-icon {
  display: grid;
  width: 28px;
  height: 28px;
  flex: none;
  place-items: center;
  border-radius: 9px;
  color: #ffc0e5;
  background: rgba(255, 167, 220, 0.11);
}

.feature-chip strong,
.feature-chip small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feature-chip strong {
  color: #e9daf5;
  font-size: 0.58rem;
  font-weight: 700;
}

.feature-chip small {
  margin-top: 0.14rem;
  color: #8f7da9;
  font-size: 0.51rem;
}

.visual-footnote {
  margin-top: 1.1rem;
  color: #7f6d9e;
  font-size: 0.54rem;
  letter-spacing: 0.03em;
}

.auth-form-side {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: clamp(1.5rem, 4vw, 3.5rem);
  background:
    radial-gradient(circle at 96% 4%, rgba(249, 137, 202, 0.13), transparent 26%),
    radial-gradient(circle at 6% 96%, rgba(92, 225, 215, 0.08), transparent 30%),
    #100b1d;
}

.auth-form-side::before {
  position: absolute;
  inset: 0;
  opacity: 0.38;
  background-image:
    linear-gradient(rgba(255, 228, 249, 0.025) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 228, 249, 0.025) 1px, transparent 1px);
  background-size: 58px 58px;
  content: '';
  mask-image: radial-gradient(ellipse at 55% 35%, black, transparent 82%);
  pointer-events: none;
}

.form-sparkles {
  position: absolute;
  inset: 0;
  color: rgba(255, 190, 228, 0.38);
  pointer-events: none;
}

.form-sparkles span { position: absolute; }
.form-sparkles span:nth-child(1) { top: 10%; left: 11%; font-size: 1.6rem; }
.form-sparkles span:nth-child(2) { right: 8%; bottom: 14%; font-size: 2rem; color: rgba(255, 182, 221, 0.3); }
.form-sparkles span:nth-child(3) { top: 26%; right: 12%; color: rgba(159, 242, 231, 0.32); }

.auth-form-wrap {
  position: relative;
  z-index: 1;
  width: min(430px, 100%);
  animation: form-in 0.5s ease-out both;
}

.mobile-brand { display: none; margin-bottom: 1.8rem; }

.auth-heading {
  margin-bottom: 1.35rem;
}

.auth-eyebrow {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.65rem;
  color: #ffa9d7;
  font-size: 0.58rem;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.auth-eyebrow span {
  width: 22px;
  height: 1px;
  background: linear-gradient(90deg, #ff9fd1, transparent);
}

.auth-heading h2 {
  color: #fff7fd;
  font-size: clamp(1.75rem, 3vw, 2.3rem);
  font-weight: 800;
  letter-spacing: -0.065em;
  line-height: 1.08;
}

.auth-subtitle {
  max-width: 390px;
  margin-top: 0.62rem;
  color: #9787ae;
  font-size: 0.71rem;
  line-height: 1.72;
}

.form-card {
  padding: clamp(1rem, 2.5vw, 1.35rem);
  border: 1px solid rgba(255, 205, 239, 0.11);
  border-radius: 22px;
  background:
    linear-gradient(145deg, rgba(255, 240, 251, 0.055), rgba(255, 255, 255, 0.018)),
    rgba(23, 16, 40, 0.78);
  box-shadow: 0 26px 70px rgba(3, 1, 12, 0.38), inset 0 1px 0 rgba(255, 255, 255, 0.06);
  backdrop-filter: blur(20px);
}

.auth-legal {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  margin-top: 1.1rem;
  color: #67587f;
  font-size: 0.54rem;
  line-height: 1.6;
  text-align: center;
}

@keyframes twinkle {
  from { opacity: 0.4; transform: scale(0.8) rotate(-8deg); }
  to { opacity: 1; transform: scale(1.1) rotate(8deg); }
}

@keyframes form-in {
  from { opacity: 0; transform: translateY(14px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (max-width: 1040px) {
  .auth-page { grid-template-columns: 1fr; }
  .auth-visual { display: none; }
  .mobile-brand { display: block; }
  .auth-form-side { min-height: 100svh; }
}

@media (max-width: 560px) {
  .auth-form-side { align-items: flex-start; padding: 1.25rem 1rem 2rem; }
  .form-card { padding: 1rem; border-radius: 18px; }
}

@media (prefers-reduced-motion: reduce) {
  .visual-stars span,
  .auth-form-wrap { animation: none; }
}
</style>
