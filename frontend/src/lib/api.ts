import type {
  ApiErrorBody,
  AuthPayload,
  CreatedOAuthClient,
  OAuthAuthorizeView,
  OAuthClient,
  PageResponse,
  SetupInstallRequest,
  SetupResult,
  SetupStatus,
  User,
} from '@/types'

const API_BASE = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')

let accessToken: string | null = null
let refreshInFlight: Promise<AuthPayload | null> | null = null
let sessionLostHandler: (() => void) | null = null
let sessionRefreshedHandler: ((payload: AuthPayload) => void) | null = null

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fields: Record<string, string>

  constructor(status: number, code: string, message: string, fields: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fields = fields
  }
}

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function setSessionLostHandler(handler: () => void) {
  sessionLostHandler = handler
}

export function setSessionRefreshedHandler(handler: (payload: AuthPayload) => void) {
  sessionRefreshedHandler = handler
}

interface RequestOptions {
  retry?: boolean
  auth?: boolean
}

async function requestOnce<T>(path: string, init: RequestInit = {}, auth = true): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (auth && accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers,
    credentials: 'include',
    cache: 'no-store',
  })

  if (response.status === 204) return undefined as T

  const text = await response.text()
  let body: unknown = null
  if (text) {
    try {
      body = JSON.parse(text)
    } catch {
      body = null
    }
  }

  if (!response.ok) {
    const errorBody = (body ?? {}) as ApiErrorBody
    throw new ApiError(
      response.status,
      errorBody.code || 'REQUEST_FAILED',
      errorBody.message || '请求失败，请稍后重试',
      errorBody.fields || {},
    )
  }

  return body as T
}

export async function request<T>(
  path: string,
  init: RequestInit = {},
  options: RequestOptions = {},
): Promise<T> {
  const retry = options.retry ?? true
  const auth = options.auth ?? true
  try {
    return await requestOnce<T>(path, init, auth)
  } catch (error) {
    if (retry && auth && error instanceof ApiError && error.status === 401) {
      const refreshed = await refreshAccessToken()
      if (refreshed) return requestOnce<T>(path, init, true)
      sessionLostHandler?.()
    }
    throw error
  }
}

export async function refreshAccessToken(): Promise<AuthPayload | null> {
  if (refreshInFlight) return refreshInFlight

  refreshInFlight = requestOnce<AuthPayload>('/auth/refresh', { method: 'POST' }, false)
    .then((payload) => {
      accessToken = payload.accessToken
      sessionRefreshedHandler?.(payload)
      return payload
    })
    .catch(() => {
      accessToken = null
      return null
    })
    .finally(() => {
      refreshInFlight = null
    })

  return refreshInFlight
}

export const api = {
  setupStatus() {
    return request<SetupStatus>('/setup/status', {}, { auth: false, retry: false })
  },

  installSetup(input: SetupInstallRequest, setupToken: string) {
    return request<SetupResult>(
      '/setup/install',
      {
        method: 'POST',
        headers: { 'X-Setup-Token': setupToken },
        body: JSON.stringify(input),
      },
      { auth: false, retry: false },
    )
  },

  login(identifier: string, password: string) {
    return request<AuthPayload>(
      '/auth/login',
      { method: 'POST', body: JSON.stringify({ identifier, password }) },
      { auth: false, retry: false },
    )
  },

  register(input: { nickname: string; email: string; password: string }) {
    return request<AuthPayload>(
      '/auth/register',
      { method: 'POST', body: JSON.stringify(input) },
      { auth: false, retry: false },
    )
  },

  logout() {
    return request<void>('/auth/logout', { method: 'POST' }, { auth: false, retry: false })
  },

  me() {
    return request<User>('/auth/me')
  },

  users(query = '', page = 0, size = 20) {
    const params = new URLSearchParams({ query, page: String(page), size: String(size) })
    return request<PageResponse<User>>(`/admin/users?${params}`)
  },

  updateUser(id: number, patch: { enabled?: boolean; role?: 'USER' | 'ADMIN' }) {
    return request<User>(`/admin/users/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(patch),
    })
  },

  oauthAuthorize(params: Record<string, string>) {
    const search = new URLSearchParams()
    Object.entries(params).forEach(([name, value]) => {
      if (value) search.set(name, value)
    })
    return request<OAuthAuthorizeView>(`/oauth/authorize?${search}`)
  },

  decideOAuthAuthorize(input: {
    clientId: string
    redirectUri: string
    responseType: string
    scope: string
    state?: string
    nonce?: string
    codeChallenge?: string
    codeChallengeMethod?: string
    prompt?: string
    loginHint?: string
    maxAge?: string
    approved: boolean
  }) {
    return request<{ redirectUrl: string }>('/oauth/authorize/decision', {
      method: 'POST',
      body: JSON.stringify(input),
    })
  },

  oauthClients() {
    return request<OAuthClient[]>('/admin/oauth-clients')
  },

  createOAuthClient(input: {
    name: string
    description: string
    redirectUris: string[]
    postLogoutRedirectUris: string[]
    scopes: string[]
    publicClient: boolean
  }) {
    return request<CreatedOAuthClient>('/admin/oauth-clients', {
      method: 'POST',
      body: JSON.stringify(input),
    })
  },

  updateOAuthClient(id: number, patch: {
    name?: string
    description?: string
    redirectUris?: string[]
    postLogoutRedirectUris?: string[]
    scopes?: string[]
    enabled?: boolean
    publicClient?: boolean
  }) {
    return request<OAuthClient>(`/admin/oauth-clients/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(patch),
    })
  },

  rotateOAuthClientSecret(id: number) {
    return request<CreatedOAuthClient>(`/admin/oauth-clients/${id}/secret`, { method: 'POST' })
  },
}
