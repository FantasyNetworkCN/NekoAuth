export type Role = 'USER' | 'ADMIN'

export interface User {
  id: number
  username: string
  email: string
  displayName: string
  role: Role
  enabled: boolean
  createdAt: string
  updatedAt: string
  lastLoginAt: string | null
}

export interface AuthPayload {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: User
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ApiErrorBody {
  timestamp?: string
  status?: number
  code?: string
  message?: string
  fields?: Record<string, string>
}

export interface SetupStatus {
  required: boolean
  tokenRequired: boolean
}

export interface SetupInstallRequest {
  adminUsername: string
  adminEmail: string
  adminDisplayName: string
  adminPassword: string
  mysqlHost: string
  mysqlPort: number
  mysqlDatabase: string
  mysqlUsername: string
  mysqlPassword: string
  redisHost: string
  redisPort: number
  redisPassword: string
  redisDatabase: number
}

export interface SetupResult {
  adminUsername: string
  restartRequired: boolean
  message: string
}

export interface OAuthScopeDetail {
  name: string
  label: string
  description: string
}

export interface OAuthAuthorizeView {
  clientId: string
  clientName: string
  description: string | null
  redirectHost: string
  username: string
  displayName: string
  scopes: string[]
  scopeDetails: OAuthScopeDetail[]
  previouslyApproved: boolean
  redirectUrl: string | null
}

export interface OAuthClient {
  id: number
  clientId: string
  name: string
  description: string | null
  redirectUris: string[]
  scopes: string[]
  publicClient: boolean
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface CreatedOAuthClient {
  client: OAuthClient
  clientSecret: string | null
}
