export type Role = 'USER' | 'ADMIN'

export interface User {
  id: number
  username: string
  nickname: string | null
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

export type MailSecurity = 'STARTTLS' | 'SSL' | 'NONE'

export interface RegistrationPolicy {
  enabled: boolean
  emailDomains: string[]
}

export interface AdminSettings {
  serviceName: string
  registrationEnabled: boolean
  emailDomainWhitelist: string[]
  updatedAt: string
  smtp: SmtpSettings
}

export interface SmtpSettings {
  enabled: boolean
  host: string
  port: number
  username: string
  passwordConfigured: boolean
  security: MailSecurity
  authEnabled: boolean
  fromEmail: string
  fromName: string
  connectionTimeoutMs: number
  readTimeoutMs: number
  writeTimeoutMs: number
}

export interface SettingsUpdate {
  serviceName: string
  registrationEnabled: boolean
  emailDomainWhitelist: string[]
  smtp: Omit<SmtpSettings, 'passwordConfigured'> & {
    password?: string
    clearPassword: boolean
  }
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
  reauthRequired: boolean
  loginHint: string | null
}

export interface OAuthClient {
  id: number
  clientId: string
  name: string
  description: string | null
  redirectUris: string[]
  postLogoutRedirectUris: string[]
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
