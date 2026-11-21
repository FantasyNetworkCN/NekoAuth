export type Role = 'USER' | 'ADMIN' | 'SUPER_ADMIN'

export interface User {
  id: number
  username: string
  nickname: string | null
  email?: string
  phone?: string
  displayName: string
  avatarUrl: string
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

export interface VerificationCodeSent {
  message: string
  expiresInSeconds: number
  resendAfterSeconds: number
}

export interface Session {
  id: number
  ipAddress: string | null
  userAgent: string | null
  device: DeviceProfile | null
  createdAt: string
  lastUsedAt: string | null
  expiresAt: string
  current: boolean
}

export type DeviceType = 'desktop' | 'mobile' | 'tablet' | 'unknown'

export interface DeviceProfile {
  type: DeviceType
  platform: string | null
  platformVersion: string | null
  architecture: string | null
  model: string | null
  browser: string | null
  browserVersion: string | null
  screen: string | null
  timezone: string | null
  touch: boolean | null
  source: string | null
}

export interface RegistrationCaptcha {
  id: string
  image: string
  expiresInSeconds: number
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface AuditLog {
  occurredAt: string
  operatorName: string
  targetName: string | null
  actionLabel: string
  summary: string
  sourceAddress: string | null
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
  emailVerificationRequired: boolean
  smsRegistrationEnabled: boolean
  emailDomains: string[]
  emailCodeExpiresInSeconds: number
  emailCodeResendAfterSeconds: number
  emailCodeDailyLimit: number
  emailCodeWindowSeconds: number
}

export interface AdminSettings {
  serviceName: string
  registrationEnabled: boolean
  emailDomainWhitelist: string[]
  updatedAt: string
  smtp: SmtpSettings
  sms?: SmsSettings
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

export interface SmsSettings {
  enabled: boolean
  accessKeyId: string
  accessKeySecretConfigured: boolean
  signName: string
  templateCode: string
  schemeName: string
  codeParameter: string
  minutesParameter: string
}

export type SmsSettingsUpdate = Omit<SmsSettings, 'accessKeySecretConfigured' | 'templateCode'> & {
  accessKeySecret?: string
  clearAccessKeySecret: boolean
}

export interface SettingsUpdate {
  serviceName: string
  registrationEnabled: boolean
  emailDomainWhitelist: string[]
  smtp: Omit<SmtpSettings, 'passwordConfigured'> & {
    password?: string
    clearPassword: boolean
  }
  sms: SmsSettingsUpdate
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
  accessTokenTtlSeconds: number | null
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface CreatedOAuthClient {
  client: OAuthClient
  clientSecret: string | null
}
