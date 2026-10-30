<script setup lang="ts">
withDefaults(defineProps<{
  pose?: 'greet' | 'wink' | 'guard' | 'cheer' | 'sleep'
  compact?: boolean
}>(), {
  pose: 'greet',
  compact: false,
})
</script>

<template>
  <svg
    class="neko-mascot"
    :class="[`pose-${pose}`, { compact }]"
    viewBox="0 0 420 500"
    role="img"
    aria-label="梦幻网络猫娘向导小梦"
  >
    <title>梦幻网络猫娘向导小梦</title>
    <defs>
      <linearGradient id="nekoHair" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0" stop-color="#fff2fb" />
        <stop offset="0.38" stop-color="#f4b9dd" />
        <stop offset="0.75" stop-color="#c58df2" />
        <stop offset="1" stop-color="#8872e8" />
      </linearGradient>
      <linearGradient id="nekoDress" x1="0" y1="0" x2="0.9" y2="1">
        <stop offset="0" stop-color="#fff8fd" />
        <stop offset="0.55" stop-color="#e7d8ff" />
        <stop offset="1" stop-color="#a98bec" />
      </linearGradient>
      <linearGradient id="nekoRibbon" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0" stop-color="#ff8ec7" />
        <stop offset="1" stop-color="#f44f9b" />
      </linearGradient>
      <radialGradient id="nekoGlow" cx="50%" cy="45%" r="55%">
        <stop offset="0" stop-color="#ffd6ee" stop-opacity="0.9" />
        <stop offset="1" stop-color="#ffd6ee" stop-opacity="0" />
      </radialGradient>
      <filter id="nekoShadow" x="-30%" y="-30%" width="160%" height="180%">
        <feDropShadow dx="0" dy="12" stdDeviation="12" flood-color="#5a2b83" flood-opacity="0.2" />
      </filter>
      <filter id="nekoGlowFilter" x="-50%" y="-50%" width="200%" height="200%">
        <feGaussianBlur stdDeviation="5" result="blur" />
        <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
      </filter>
    </defs>

    <circle cx="210" cy="252" r="190" fill="url(#nekoGlow)" />
    <g class="sparkles" fill="#fff" filter="url(#nekoGlowFilter)">
      <path d="M60 120l4 12 12 4-12 4-4 12-4-12-12-4 12-4z" />
      <path d="M356 92l3 9 9 3-9 3-3 9-3-9-9-3 9-3z" opacity=".78" />
      <circle cx="368" cy="220" r="4" opacity=".75" />
      <circle cx="42" cy="268" r="3" opacity=".65" />
      <path d="M340 338l2.5 8 8 2.5-8 2.5-2.5 8-2.5-8-8-2.5 8-2.5z" opacity=".8" />
    </g>

    <g class="tail" fill="none" stroke-linecap="round">
      <path d="M278 386C373 390 398 322 358 285c-25-23-58-3-47 23 11 24 50 15 47-10"
            stroke="#6c4cb9" stroke-width="38" opacity=".22" transform="translate(6 11)" />
      <path d="M278 386C373 390 398 322 358 285c-25-23-58-3-47 23 11 24 50 15 47-10"
            stroke="url(#nekoHair)" stroke-width="30" />
      <path d="M359 286c-12-6-24 4-22 15" stroke="#fff4fc" stroke-width="8" opacity=".8" />
    </g>

    <g class="body" filter="url(#nekoShadow)">
      <path d="M104 493c8-89 35-150 106-150s99 61 107 150z" fill="url(#nekoDress)" />
      <path d="M157 354c15 27 34 45 53 45s39-18 54-45l-18-12h-72z" fill="#6e5ab5" opacity=".2" />
      <path d="M128 396c-26 10-40 42-30 63 8 16 28 12 38-8l28-52z"
            fill="url(#nekoDress)" stroke="#e7d6ff" stroke-width="3" />
      <path d="M292 396c26 10 40 42 30 63-8 16-28 12-38-8l-28-52z"
            fill="url(#nekoDress)" stroke="#e7d6ff" stroke-width="3" />
      <ellipse cx="116" cy="459" rx="18" ry="23" fill="#ffe5f2" transform="rotate(-22 116 459)" />
      <ellipse cx="304" cy="459" rx="18" ry="23" fill="#ffe5f2" transform="rotate(22 304 459)" />
      <path d="M105 470c9-9 23-6 29 2-9 13-24 17-29-2z" fill="#f7c7e1" opacity=".8" />
      <path d="M315 470c-9-9-23-6-29 2 9 13 24 17 29-2z" fill="#f7c7e1" opacity=".8" />

      <path d="M146 393c15 33 34 61 64 61s49-28 64-61l-34-19-30 17-30-17z" fill="#fff9ff" />
      <path d="M210 402l18 20-18 48-18-48z" fill="#b69bea" opacity=".55" />
      <path d="M178 391l32 31 32-31 30 19c-17 34-37 66-62 66s-45-32-62-66z"
            fill="url(#nekoDress)" stroke="#f7eaff" stroke-width="2" />

      <g class="ribbon">
        <path d="M210 417c-30-29-59-13-50 14 7 22 34 18 50 3z" fill="url(#nekoRibbon)" />
        <path d="M210 417c30-29 59-13 50 14-7 22-34 18-50 3z" fill="url(#nekoRibbon)" />
        <path d="M199 423l11 16 11-16-11-25z" fill="#e84592" />
        <circle cx="210" cy="426" r="12" fill="#ffd2e7" stroke="#f35a9f" stroke-width="3" />
        <circle cx="206" cy="422" r="3" fill="#fff" opacity=".9" />
      </g>

      <path d="M153 365c-18 7-29 27-26 45 2 12 12 19 20 10 9-10 10-31 23-44z"
            fill="#fff8fd" stroke="#efdefa" stroke-width="2" />
      <path d="M267 365c18 7 29 27 26 45-2 12-12 19-20 10-9-10-10-31-23-44z"
            fill="#fff8fd" stroke="#efdefa" stroke-width="2" />
    </g>

    <g class="head" filter="url(#nekoShadow)">
      <g class="twintails">
        <path d="M112 173c-51 17-73 72-58 130 9 36 37 41 49 16 11-23-9-37-23-26-14 12-5 34 12 28"
              fill="none" stroke="#714fae" stroke-width="42" stroke-linecap="round" opacity=".18"
              transform="translate(9 12)" />
        <path d="M112 173c-51 17-73 72-58 130 9 36 37 41 49 16 11-23-9-37-23-26-14 12-5 34 12 28"
              fill="none" stroke="url(#nekoHair)" stroke-width="34" stroke-linecap="round" />
        <path d="M308 173c51 17 73 72 58 130-9 36-37 41-49 16-11-23 9-37 23-26 14 12 5 34-12 28"
              fill="none" stroke="#714fae" stroke-width="42" stroke-linecap="round" opacity=".18"
              transform="translate(-9 12)" />
        <path d="M308 173c51 17 73 72 58 130-9 36-37 41-49 16-11-23 9-37 23-26 14 12 5 34-12 28"
              fill="none" stroke="url(#nekoHair)" stroke-width="34" stroke-linecap="round" />
      </g>

      <g class="ears">
        <path d="M134 122 113 49c-2-8 8-13 14-8l47 49z" fill="url(#nekoHair)" stroke="#a778d8" stroke-width="3" />
        <path d="M286 122 307 49c2-8-8-13-14-8l-47 49z" fill="url(#nekoHair)" stroke="#a778d8" stroke-width="3" />
        <path d="M137 106 124 63l31 33z" fill="#ffc6e1" />
        <path d="M283 106 296 63l-31 33z" fill="#ffc6e1" />
        <path d="M140 91c4-8 10-13 16-16" stroke="#fff" stroke-width="3" stroke-linecap="round" opacity=".8" />
        <path d="M280 91c-4-8-10-13-16-16" stroke="#fff" stroke-width="3" stroke-linecap="round" opacity=".8" />
      </g>

      <ellipse cx="210" cy="203" rx="105" ry="103" fill="url(#nekoHair)" stroke="#aa7fdd" stroke-width="3" />
      <ellipse cx="210" cy="215" rx="83" ry="82" fill="#fff2f8" />
      <path d="M129 185c7-66 45-91 81-91s74 25 81 91c-18-25-35-40-57-47-9 27-31 41-69 35-18-3-27-10-36-23z"
            fill="url(#nekoHair)" />
      <path d="M169 102c-4 28-18 58-44 84M251 102c4 28 18 58 44 84" stroke="#fff5fc" stroke-width="4" opacity=".7" />

      <g class="ahoge">
        <path d="M201 110c-18-34 8-61 33-56 19 4 20 24 6 29-9 3-15-2-14-10"
              fill="none" stroke="#b888e5" stroke-width="5" stroke-linecap="round" />
      </g>

      <g class="face">
        <ellipse cx="170" cy="235" rx="12" ry="7" fill="#ff9fc4" opacity=".3" />
        <ellipse cx="250" cy="235" rx="12" ry="7" fill="#ff9fc4" opacity=".3" />

        <g class="eye eye-left">
          <ellipse cx="172" cy="207" rx="24" ry="29" fill="#4d2c75" />
          <ellipse cx="172" cy="211" rx="18" ry="23" fill="#9c6ed9" />
          <ellipse cx="172" cy="217" rx="11" ry="15" fill="#66e5e8" />
          <ellipse cx="172" cy="218" rx="6" ry="9" fill="#263255" />
          <circle cx="164" cy="198" r="7" fill="#fff" />
          <circle cx="180" cy="222" r="3" fill="#fff" opacity=".9" />
          <path d="M147 185c9-12 22-17 35-15" stroke="#553676" stroke-width="5" stroke-linecap="round" opacity=".7" />
        </g>
        <g class="eye eye-right">
          <ellipse cx="248" cy="207" rx="24" ry="29" fill="#4d2c75" />
          <ellipse cx="248" cy="211" rx="18" ry="23" fill="#9c6ed9" />
          <ellipse cx="248" cy="217" rx="11" ry="15" fill="#66e5e8" />
          <ellipse cx="248" cy="218" rx="6" ry="9" fill="#263255" />
          <circle cx="240" cy="198" r="7" fill="#fff" />
          <circle cx="256" cy="222" r="3" fill="#fff" opacity=".9" />
          <path d="M231 185c9-12 22-17 35-15" stroke="#553676" stroke-width="5" stroke-linecap="round" opacity=".7" />
        </g>

        <g class="happy-eyes" fill="none" stroke="#59327e" stroke-width="6" stroke-linecap="round">
          <path d="M151 210c10-17 28-19 39-3" />
          <path d="M230 207c12-16 29-14 39 2" />
        </g>

        <path d="M205 238c4 4 8 4 12 0" fill="none" stroke="#d889ae" stroke-width="3" stroke-linecap="round" />
        <path class="mouth mouth-smile" d="M198 253c8 9 19 9 27 0" fill="none" stroke="#b65c84" stroke-width="3" stroke-linecap="round" />
        <path class="mouth mouth-open" d="M197 251c9 16 28 16 37 0-8 4-29 4-37 0z" fill="#d86f98" />
        <path class="mouth mouth-sleep" d="M205 254c5 5 11 5 16 0" fill="none" stroke="#b65c84" stroke-width="3" stroke-linecap="round" />

        <g class="whiskers" stroke="#d89bb8" stroke-width="2.5" stroke-linecap="round" opacity=".75">
          <path d="M128 232l-24-5M129 242l-25 7M292 232l24-5M291 242l25 7" />
        </g>
      </g>

      <g class="hairpin">
        <path d="M292 128l7 15 16 2-12 12 3 16-14-8-14 8 3-16-12-12 16-2z" fill="#ffe477" stroke="#f9b64e" stroke-width="2" />
        <circle cx="292" cy="145" r="4" fill="#fff8c8" />
      </g>
      <circle cx="121" cy="169" r="11" fill="#ff9cca" stroke="#fff4fa" stroke-width="3" />
      <circle cx="299" cy="178" r="8" fill="#9b84e8" stroke="#fff4fa" stroke-width="3" />
    </g>

    <g class="guard-shield" filter="url(#nekoGlowFilter)">
      <path d="M310 344c38 12 67 11 75 6 2 55-20 92-75 111-55-19-77-56-75-111 8 5 37 6 75-6z"
            fill="#7c67d9" opacity=".92" stroke="#fff0ff" stroke-width="4" />
      <path d="M310 365l25 9v24c0 24-11 40-25 49-14-9-25-25-25-49v-24z" fill="#f9efff" opacity=".25" />
      <path d="M295 399l11 11 21-25" fill="none" stroke="#fff" stroke-width="7" stroke-linecap="round" stroke-linejoin="round" />
    </g>

    <g class="paw-glow" fill="none" stroke="#fff" stroke-width="3" stroke-linecap="round" opacity=".65" filter="url(#nekoGlowFilter)">
      <path d="M70 364c8-8 18-3 18 6 0 8-8 13-18 8-10 5-18 0-18-8 0-9 10-14 18-6z" />
      <circle cx="57" cy="351" r="3.5" /><circle cx="65" cy="345" r="3.5" /><circle cx="75" cy="346" r="3.5" /><circle cx="83" cy="353" r="3.5" />
    </g>
  </svg>
</template>

<style scoped>
.neko-mascot {
  display: block;
  width: min(100%, 440px);
  overflow: visible;
  filter: saturate(1.04);
}

.neko-mascot.compact {
  width: 110px;
}

.tail {
  transform-origin: 285px 375px;
  animation: tail-sway 4.8s ease-in-out infinite;
}

.head {
  animation: neko-float 5.4s ease-in-out infinite;
}

.sparkles {
  animation: sparkle 3.2s ease-in-out infinite alternate;
}

.ahoge {
  transform-origin: 218px 112px;
  animation: ahoge-bounce 3.8s ease-in-out infinite;
}

.happy-eyes,
.guard-shield {
  display: none;
}

.pose-wink .eye-left,
.pose-sleep .eye-left,
.pose-sleep .eye-right,
.pose-cheer .eye-left,
.pose-cheer .eye-right {
  display: none;
}

.pose-wink .happy-eyes,
.pose-sleep .happy-eyes,
.pose-cheer .happy-eyes {
  display: block;
}

.pose-wink .mouth-smile,
.pose-sleep .mouth-smile,
.pose-cheer .mouth-smile {
  display: none;
}

.pose-sleep .mouth-open,
.pose-cheer .mouth-open {
  display: none;
}

.pose-sleep .eye-right {
  display: block;
}

.pose-sleep .happy-eyes path:first-child {
  display: block;
}

.pose-sleep .happy-eyes path:last-child {
  display: none;
}

.pose-sleep .mouth-sleep {
  display: block;
}

.pose-guard .guard-shield {
  display: block;
  animation: shield-pulse 3s ease-in-out infinite;
}

.pose-guard .mouth-smile {
  display: none;
}

.pose-guard .mouth-open {
  display: block;
}

.pose-cheer .body {
  transform-origin: 210px 390px;
  animation: cheer 2.8s ease-in-out infinite;
}

@keyframes neko-float {
  0%, 100% { transform: translateY(0) rotate(-0.4deg); }
  50% { transform: translateY(-7px) rotate(0.5deg); }
}

@keyframes tail-sway {
  0%, 100% { transform: rotate(-2deg); }
  50% { transform: rotate(5deg); }
}

@keyframes sparkle {
  from { opacity: 0.45; transform: scale(0.96); }
  to { opacity: 1; transform: scale(1.04); }
}

@keyframes ahoge-bounce {
  0%, 100% { transform: rotate(-3deg); }
  50% { transform: rotate(5deg); }
}

@keyframes shield-pulse {
  0%, 100% { transform: translateY(0); filter: drop-shadow(0 0 5px rgba(255, 194, 239, 0.25)); }
  50% { transform: translateY(-5px); filter: drop-shadow(0 0 16px rgba(255, 194, 239, 0.65)); }
}

@keyframes cheer {
  0%, 100% { transform: rotate(-1deg); }
  50% { transform: rotate(1.5deg) translateY(-4px); }
}

@media (prefers-reduced-motion: reduce) {
  .tail, .head, .sparkles, .ahoge, .guard-shield, .body {
    animation: none;
  }
}
</style>
