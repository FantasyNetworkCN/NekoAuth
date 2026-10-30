<script setup lang="ts">
withDefaults(defineProps<{ name: string; size?: number }>(), { size: 20 })

const paths: Record<string, string> = {
  grid: 'M3 3h7v7H3zM14 3h7v7h-7zM14 14h7v7h-7zM3 14h7v7H3z',
  user: 'M20 21a8 8 0 0 0-16 0M12 13a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z',
  devices: 'M3 5h18v11H3zM8 20h8M12 16v4',
  shield: 'M12 3 4 6v5c0 5 3.4 8.7 8 10 4.6-1.3 8-5 8-10V6l-8-3Z',
  logout: 'M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9',
  menu: 'M4 6h16M4 12h16M4 18h16',
  close: 'M18 6 6 18M6 6l12 12',
  mail: 'M4 5h16v14H4zM4 7l8 6 8-6',
  lock: 'M6 10h12v10H6zM8 10V7a4 4 0 0 1 8 0v3',
  eye: 'M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Zm10 3a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z',
  'eye-off': 'm3 3 18 18M10.6 10.6a2 2 0 0 0 2.8 2.8M9.9 5.2A10.8 10.8 0 0 1 12 5c6.5 0 10 7 10 7a16.6 16.6 0 0 1-2.2 3.2M6.7 6.7C3.7 8.5 2 12 2 12s3.5 7 10 7c1.1 0 2.1-.2 3-.5',
  'arrow-right': 'M5 12h14M13 6l6 6-6 6',
  spark: 'm12 3 1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9L12 3Z',
  refresh: 'M20 11a8 8 0 1 0-2.3 6.7M20 4v7h-7',
  trash: 'M3 6h18M8 6V4h8v2M6 6l1 15h10l1-15M10 11v6M14 11v6',
  search: 'm21 21-4.3-4.3M19 11a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z',
  check: 'm5 12 4 4L19 6',
  alert: 'M12 3 2 21h20L12 3ZM12 9v5M12 18h.01',
  key: 'M14 7a4 4 0 1 1-3.5 5.9L4 19.4V22H2v-3l8.1-8.1A4 4 0 0 1 14 7Z',
  clock: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20ZM12 6v6l4 2',
  monitor: 'M3 4h18v12H3zM8 20h8M12 16v4',
  'chevron-left': 'm15 18-6-6 6-6',
  'chevron-right': 'm9 18 6-6-6-6',
  users: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM22 21v-2a4 4 0 0 0-3-3.9M16 3.1a4 4 0 0 1 0 7.8',
  'user-check': 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM16 11l2 2 4-4',
  apps: 'M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z',
  paw: 'M12 21c-4.2 0-7-2.8-7-6 0-2.3 2-4 3.8-5.4C10.2 8.3 10.8 6 12 6s1.8 2.3 3.2 3.6C17 11 19 12.7 19 15c0 3.2-2.8 6-7 6ZM5 9a2 2 0 1 0 0-4 2 2 0 0 0 0 4ZM9 6a2 2 0 1 0 0-4 2 2 0 0 0 0 4ZM15 6a2 2 0 1 0 0-4 2 2 0 0 0 0 4ZM19 9a2 2 0 1 0 0-4 2 2 0 0 0 0 4Z',
  copy: 'M9 9h11v11H9zM5 15H3V3h12v2',
  globe: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20ZM2 12h20M12 2c3 3.2 4.5 6.5 4.5 10S15 18.8 12 22c-3-3.2-4.5-6.5-4.5-10S9 5.2 12 2Z',
  'shield-cat': 'M12 3 4 6v5c0 5 3.4 8.7 8 10 4.6-1.3 8-5 8-10V6l-8-3ZM8 7l1.8 3.4M16 7l-1.8 3.4M9 14h.01M15 14h.01M10 17c1.2 1 2.8 1 4 0',
  heart: 'M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8l1.1 1.1L12 21l7.8-7.5 1.1-1.1a5.5 5.5 0 0 0-.1-7.8Z',
  plus: 'M12 5v14M5 12h14',
  edit: 'M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5Z',
  info: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20ZM12 11v6M12 7h.01',
  sliders: 'M4 6h8M16 6h4M4 12h4M12 12h8M4 18h11M19 18h1M12 3v6M8 9v6M15 15v6',
}
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path :d="paths[name] || ''" />
  </svg>
</template>
