import { computed, reactive } from 'vue'
import { api, refreshAccessToken, setAccessToken, setSessionLostHandler, setSessionRefreshedHandler } from '@/lib/api'
import type { AuthPayload, User } from '@/types'

interface AuthState {
  user: User | null
  ready: boolean
}

const state = reactive<AuthState>({
  user: null,
  ready: false,
})

let refreshTimer: ReturnType<typeof setTimeout> | undefined
let expiredHandler: (() => void) | null = null

function apply(payload: AuthPayload) {
  state.user = payload.user
  setAccessToken(payload.accessToken)
  scheduleRefresh(payload.expiresIn)
}

function clear() {
  state.user = null
  state.ready = true
  setAccessToken(null)
  if (refreshTimer) clearTimeout(refreshTimer)
  refreshTimer = undefined
}

function scheduleRefresh(expiresIn: number) {
  if (refreshTimer) clearTimeout(refreshTimer)
  const delay = Math.max(10_000, Math.max(0, expiresIn - 60) * 1000)
  refreshTimer = setTimeout(() => {
    void refreshSilently()
  }, delay)
}

async function refreshSilently() {
  const payload = await refreshAccessToken()
  if (!payload) {
    clear()
    expiredHandler?.()
  }
}

setSessionRefreshedHandler((payload) => {
  state.user = payload.user
  scheduleRefresh(payload.expiresIn)
})

setSessionLostHandler(() => {
  clear()
  expiredHandler?.()
})

export const auth = {
  state,
  isAuthenticated: computed(() => state.user !== null),
  isAdmin: computed(() => state.user?.role === 'ADMIN'),

  setExpiredHandler(handler: () => void) {
    expiredHandler = handler
  },

  async bootstrap() {
    if (state.ready) return
    const payload = await refreshAccessToken()
    if (payload) apply(payload)
    state.ready = true
  },

  async login(identifier: string, password: string) {
    const payload = await api.login(identifier, password)
    apply(payload)
  },

  async register(input: { email: string; password: string; verificationCode: string }) {
    const payload = await api.register(input)
    apply(payload)
  },

  async logout() {
    try {
      await api.logout()
    } finally {
      clear()
    }
  },

  clear,
}
