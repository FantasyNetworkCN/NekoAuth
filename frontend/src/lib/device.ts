import type { DeviceProfile, DeviceType } from '@/types'

interface UaBrand {
  brand: string
  version: string
}

interface UaHints {
  platform?: string
  platformVersion?: string
  architecture?: string
  model?: string
  uaFullVersion?: string
  fullVersionList?: UaBrand[]
}

interface NavigatorUaData {
  mobile?: boolean
  platform?: string
  brands?: UaBrand[]
  getHighEntropyValues?: (hints: string[]) => Promise<UaHints>
}

type DeviceNavigator = Navigator & {
  userAgentData?: NavigatorUaData
  brave?: unknown
  deviceMemory?: number
}

const HIGH_ENTROPY_HINTS = [
  'platform',
  'platformVersion',
  'architecture',
  'model',
  'uaFullVersion',
  'fullVersionList',
]

export async function collectDeviceProfile(): Promise<DeviceProfile> {
  const nav = navigator as DeviceNavigator
  const userAgent = nav.userAgent || ''
  const hints = await readHighEntropyValues(nav.userAgentData)
  const platform = resolvePlatform(nav, userAgent, hints)
  const browser = resolveBrowser(nav, userAgent, hints)
  const touchPoints = Math.max(0, nav.maxTouchPoints || 0)
  const type = resolveDeviceType(nav, userAgent, hints, platform.name, touchPoints)

  return {
    type,
    platform: platform.name || null,
    platformVersion: platform.version || null,
    architecture: hints.architecture || null,
    model: modelName(userAgent, hints.model),
    browser: browser.name || null,
    browserVersion: browser.version || null,
    screen: screenDescription(),
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || null,
    touch: touchPoints > 0,
    source: hints.platform || hints.platformVersion ? 'client_hints' : 'probe',
  }
}

async function readHighEntropyValues(data?: NavigatorUaData): Promise<UaHints> {
  if (!data?.getHighEntropyValues) return {}
  try {
    return await Promise.race([
      data.getHighEntropyValues(HIGH_ENTROPY_HINTS),
      new Promise<UaHints>((resolve) => window.setTimeout(() => resolve({}), 800)),
    ])
  } catch {
    return {}
  }
}

function resolvePlatform(nav: DeviceNavigator, userAgent: string, hints: UaHints) {
  const raw = `${hints.platform || nav.userAgentData?.platform || nav.platform || ''}`.toLowerCase()
  const touchPoints = nav.maxTouchPoints || 0

  if (raw.includes('android') || /Android/i.test(userAgent)) {
    return { name: 'Android', version: androidVersion(userAgent, hints.platformVersion) }
  }
  if (/iphone|ipad|ipod/.test(raw) || /iPhone|iPad|iPod/i.test(userAgent)
    || (/mac/.test(raw) && /Macintosh/i.test(userAgent) && touchPoints > 1)) {
    return { name: 'iOS', version: iosVersion(userAgent, hints.platformVersion) }
  }
  if (raw.includes('win') || /Windows/i.test(userAgent)) {
    return { name: 'Windows', version: windowsVersion(userAgent, hints.platformVersion) }
  }
  if (raw.includes('mac') || /Macintosh/i.test(userAgent)) {
    return { name: 'macOS', version: normalizeVersion(hints.platformVersion) || macVersion(userAgent) }
  }
  if (raw.includes('chrome os') || raw.includes('chromebook') || /CrOS/i.test(userAgent)) {
    return { name: 'ChromeOS', version: normalizeVersion(hints.platformVersion) }
  }
  if (raw.includes('linux') || /Linux/i.test(userAgent)) {
    return { name: 'Linux', version: normalizeVersion(hints.platformVersion) }
  }
  return { name: null, version: null }
}

function resolveDeviceType(
  nav: DeviceNavigator,
  userAgent: string,
  hints: UaHints,
  platform: string | null,
  touchPoints: number,
): DeviceType {
  if (nav.userAgentData?.mobile === true
    || /iPhone|iPod|Windows Phone|Android.*Mobile/i.test(userAgent)) {
    return 'mobile'
  }
  if (/iPad/i.test(userAgent) || (/Macintosh/i.test(userAgent) && touchPoints > 1)
    || /Tablet/i.test(userAgent)
    || (platform === 'Android' && !/Mobile/i.test(userAgent))) {
    return 'tablet'
  }
  if (platform) return 'desktop'
  return hints.model ? 'mobile' : 'unknown'
}

function resolveBrowser(nav: DeviceNavigator, userAgent: string, hints: UaHints) {
  const brands = hints.fullVersionList?.length ? hints.fullVersionList : nav.userAgentData?.brands || []
  const brandVersion = (patterns: RegExp[]) => {
    const match = brands.find((item) => patterns.some((pattern) => pattern.test(item.brand)))
    return match ? { name: match.brand, version: match.version } : null
  }

  if (nav.brave) return { name: 'Brave', version: normalizeVersion(hints.uaFullVersion) }
  const edge = brandVersion([/Microsoft Edge/i])
  if (edge) return { name: 'Edge', version: normalizeVersion(edge.version) }
  const opera = brandVersion([/Opera/i])
  if (opera) return { name: 'Opera', version: normalizeVersion(opera.version) }
  const chrome = brandVersion([/Google Chrome/i])
  if (chrome) return { name: 'Chrome', version: normalizeVersion(chrome.version) }
  const chromium = brandVersion([/Chromium/i])

  const uaChecks: Array<[RegExp, string, RegExp]> = [
    [/Edg\/([\d.]+)/i, 'Edge', /Edg\//i],
    [/OPR\/([\d.]+)/i, 'Opera', /OPR\//i],
    [/Firefox\/([\d.]+)/i, 'Firefox', /Firefox\//i],
    [/Chrome\/([\d.]+)/i, 'Chrome', /Chrome\//i],
    [/Version\/([\d.]+).*Safari/i, 'Safari', /Safari\//i],
  ]
  for (const [versionPattern, name, presencePattern] of uaChecks) {
    if (!presencePattern.test(userAgent)) continue
    return { name, version: userAgent.match(versionPattern)?.[1] || null }
  }
  if (/curl|httpie/i.test(userAgent)) return { name: '命令行客户端', version: null }
  return chromium
    ? { name: 'Chromium', version: normalizeVersion(chromium.version) }
    : { name: '浏览器', version: null }
}

function windowsVersion(userAgent: string, platformVersion?: string): string | null {
  const version = normalizeVersion(platformVersion)
  if (version) {
    const major = Number.parseInt(version.split('.')[0] || '', 10)
    if (Number.isFinite(major)) return major >= 13 ? `11 (${version})` : `10 (${version})`
  }
  return /Windows NT 10/i.test(userAgent) ? '10/11' : null
}

function macVersion(userAgent: string): string | null {
  const value = userAgent.match(/Mac OS X ([\d_]+)/i)?.[1]
  return value ? value.replaceAll('_', '.') : null
}

function iosVersion(userAgent: string, platformVersion?: string): string | null {
  return normalizeVersion(platformVersion)
    || userAgent.match(/OS ([\d_]+) like Mac OS X/i)?.[1]?.replaceAll('_', '.')
    || null
}

function androidVersion(userAgent: string, platformVersion?: string): string | null {
  return normalizeVersion(platformVersion) || userAgent.match(/Android ([\d.]+)/i)?.[1] || null
}

function modelName(userAgent: string, model?: string): string | null {
  if (model?.trim()) return model.trim()
  return userAgent.match(/Android[^;]*;\s*([^;)]+?)(?:\s+Build|\))/i)?.[1]?.trim() || null
}

function normalizeVersion(value?: string): string | null {
  if (!value || !value.trim() || value === '0.0.0') return null
  return value.trim()
}

function screenDescription(): string | null {
  const width = window.screen?.width || 0
  const height = window.screen?.height || 0
  if (!width || !height) return null
  const ratio = window.devicePixelRatio || 1
  return `${width}×${height}${ratio > 1 ? ` @${Number(ratio.toFixed(2))}x` : ''}`
}
