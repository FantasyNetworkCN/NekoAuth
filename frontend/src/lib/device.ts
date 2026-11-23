import type { DeviceProfile, DeviceType, PointerType, RecognitionConfidence } from '@/types'

interface UaBrand {
  brand: string
  version: string
}

interface UaHints {
  mobile?: boolean
  platform?: string
  platformVersion?: string
  architecture?: string
  bitness?: string
  model?: string
  uaFullVersion?: string
  fullVersionList?: UaBrand[]
  formFactors?: string[]
  wow64?: boolean
}

interface BatteryManager {
  level: number
  charging: boolean
}

interface NavigatorConnection {
  type?: string
  effectiveType?: string
  downlink?: number
  rtt?: number
  saveData?: boolean
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
  connection?: NavigatorConnection
  getBattery?: () => Promise<BatteryManager>
}

interface DeviceCapabilities {
  pointer: PointerType
  hover: boolean | null
  touch: boolean
}

interface PlatformResolution {
  name: string | null
  version: string | null
  confidence: RecognitionConfidence | null
  evidence: string
}

interface DeviceTypeResolution {
  type: DeviceType
  confidence: RecognitionConfidence
  evidence: string
}

interface GpuReading {
  vendor: string | null
  renderer: string | null
  rawRenderer: string | null
}

interface ModelResolution {
  name: string | null
  source: string | null
}

interface BatteryReading {
  level: number | null
  charging: boolean | null
}

type ExtendedScreen = Screen & { isExtended?: boolean }

const HIGH_ENTROPY_HINTS = [
  'platform',
  'platformVersion',
  'architecture',
  'bitness',
  'model',
  'uaFullVersion',
  'fullVersionList',
  'formFactors',
  'wow64',
]

const inAppBrowsers: Array<[string, RegExp, RegExp | null]> = [
  ['微信内置浏览器', /MicroMessenger\//i, /MicroMessenger\/([\d.]+)/i],
  ['企业微信内置浏览器', /wxwork\//i, /wxwork\/([\d.]+)/i],
  ['QQ 内置浏览器', /\bQQ\//i, /\bQQ\/([\d.]+)/i],
  ['钉钉内置浏览器', /DingTalk\//i, /DingTalk\/([\d.]+)/i],
  ['飞书内置浏览器', /Lark\//i, /Lark\/([\d.]+)/i],
  ['抖音内置浏览器', /aweme|BytedanceWebview|TikTok/i, /(?:aweme|TikTok)[/_]?([\d.]+)/i],
  ['微博内置浏览器', /Weibo\//i, /Weibo\/([\d.]+)/i],
  ['小红书内置浏览器', /XHSLinkLayer|xhsdiscover/i, null],
]

export async function collectDeviceProfile(): Promise<DeviceProfile> {
  const nav = navigator as DeviceNavigator
  const userAgent = nav.userAgent || ''
  const [hints, battery, refreshRate] = await Promise.all([
    readHighEntropyValues(nav.userAgentData),
    readBattery(nav),
    measureRefreshRate(),
  ])
  const capabilities = detectCapabilities()
  const platform = resolvePlatform(nav, userAgent, hints, capabilities)
  const formFactors = normalizeFormFactors(hints.formFactors)
  const type = resolveDeviceType(nav, userAgent, hints, formFactors, platform, capabilities)
  const browser = resolveBrowser(nav, userAgent, hints)
  const gpu = readGpu()
  const model = resolveModel(userAgent, hints, platform.name)
  const architecture = resolveArchitecture(hints.architecture, nav.platform, platform.name, gpu.rawRenderer)
  const connection = nav.connection

  return {
    type: type.type,
    typeConfidence: type.confidence,
    formFactor: formFactors.length ? formFactors.map(formFactorLabel).join(', ') : null,
    platform: platform.name,
    platformVersion: platform.version,
    platformConfidence: platform.confidence,
    architecture: architecture.value,
    architectureClaim: architecture.claim,
    bitness: cleanValue(hints.bitness, 16),
    model: model.name,
    modelSource: model.source,
    browser: browser.name,
    browserVersion: browser.version,
    screen: screenDescription(),
    screenCss: screenCssDescription(),
    screenAvailable: screenAvailableDescription(),
    viewport: viewportDescription(),
    orientation: screenOrientation(),
    colorDepth: finiteNumber(window.screen?.colorDepth),
    pixelRatio: finiteNumber(window.devicePixelRatio),
    refreshRate,
    multipleScreens: typeof (window.screen as ExtendedScreen | undefined)?.isExtended === 'boolean'
      ? (window.screen as ExtendedScreen).isExtended || false
      : null,
    displayMode: displayMode(),
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || null,
    languages: cleanValue(navigator.languages?.join(', ') || navigator.language, 160),
    touch: capabilities.touch,
    maxTouchPoints: nonNegativeNumber(nav.maxTouchPoints, true),
    pointer: capabilities.pointer,
    hover: capabilities.hover,
    deviceMemory: finiteNumber(nav.deviceMemory),
    hardwareConcurrency: finiteNumber(nav.hardwareConcurrency),
    gpuVendor: gpu.vendor,
    gpuRenderer: gpu.renderer,
    batteryLevel: battery.level,
    batteryCharging: battery.charging,
    connectionType: cleanValue(connection?.type, 24),
    effectiveType: cleanValue(connection?.effectiveType, 16),
    downlinkMbps: nonNegativeNumber(connection?.downlink),
    rttMs: nonNegativeNumber(connection?.rtt, true),
    saveData: typeof connection?.saveData === 'boolean' ? connection.saveData : null,
    webdriver: typeof nav.webdriver === 'boolean' ? nav.webdriver : null,
    typeEvidence: type.evidence,
    platformEvidence: platform.evidence,
    source: hasClientHints(hints) ? 'client_hints+capabilities' : 'capabilities',
  }
}

async function readHighEntropyValues(data?: NavigatorUaData): Promise<UaHints> {
  if (!data?.getHighEntropyValues) return {}
  try {
    const values = await Promise.race([
      data.getHighEntropyValues(HIGH_ENTROPY_HINTS),
      new Promise<UaHints>((resolve) => window.setTimeout(() => resolve({}), 500)),
    ])
    return { ...values, mobile: data.mobile }
  } catch {
    return {}
  }
}

async function readBattery(nav: DeviceNavigator): Promise<BatteryReading> {
  if (typeof nav.getBattery !== 'function') return { level: null, charging: null }
  try {
    const battery = await Promise.race([
      nav.getBattery(),
      new Promise<null>((resolve) => window.setTimeout(() => resolve(null), 400)),
    ])
    if (!battery || !Number.isFinite(battery.level) || battery.level < 0 || battery.level > 1) {
      return { level: null, charging: null }
    }
    return {
      level: Number(battery.level.toFixed(2)),
      charging: typeof battery.charging === 'boolean' ? battery.charging : null,
    }
  } catch {
    return { level: null, charging: null }
  }
}

function measureRefreshRate(): Promise<number | null> {
  if (typeof window.requestAnimationFrame !== 'function') return Promise.resolve(null)
  return new Promise((resolve) => {
    const samples: number[] = []
    let startedAt = 0
    let settled = false
    const finish = () => {
      if (settled) return
      settled = true
      const deltas = samples.slice(1)
        .map((value, index) => value - (samples[index] || value))
        .filter((value) => value >= 4 && value <= 200)
        .sort((a, b) => a - b)
      if (deltas.length < 3) {
        resolve(null)
        return
      }
      const median = deltas[Math.floor(deltas.length / 2)] || 0
      const measured = 1000 / median
      const common = [24, 30, 48, 50, 60, 72, 75, 90, 100, 120, 144, 165, 180, 240, 360]
      const nearest = common.reduce((best, value) =>
        Math.abs(value - measured) < Math.abs(best - measured) ? value : best)
      resolve(Math.abs(nearest - measured) < 4 ? nearest : Math.round(measured))
    }
    const timeout = window.setTimeout(finish, 360)
    const tick = (timestamp: number) => {
      if (settled) return
      if (!startedAt) startedAt = timestamp
      samples.push(timestamp)
      if (timestamp - startedAt >= 180) {
        window.clearTimeout(timeout)
        finish()
        return
      }
      window.requestAnimationFrame(tick)
    }
    window.requestAnimationFrame(tick)
  })
}

function readGpu(): GpuReading {
  try {
    const canvas = document.createElement('canvas')
    const gl = canvas.getContext('webgl') || canvas.getContext('experimental-webgl')
    if (!gl || typeof (gl as WebGLRenderingContext).getExtension !== 'function') {
      return { vendor: null, renderer: null, rawRenderer: null }
    }
    const context = gl as WebGLRenderingContext
    const debug = context.getExtension('WEBGL_debug_renderer_info')
    const vendor = debug
      ? context.getParameter(debug.UNMASKED_VENDOR_WEBGL) as string | null
      : context.getParameter(context.VENDOR) as string | null
    const renderer = debug
      ? context.getParameter(debug.UNMASKED_RENDERER_WEBGL) as string | null
      : context.getParameter(context.RENDERER) as string | null
    return {
      vendor: cleanValue(vendor || undefined, 120),
      renderer: normalizeGpuRenderer(renderer),
      rawRenderer: cleanValue(renderer || undefined, 200),
    }
  } catch {
    return { vendor: null, renderer: null, rawRenderer: null }
  }
}

function normalizeGpuRenderer(value: string | null): string | null {
  if (!value || /^(Brave|Mozilla|Generic Renderer)$/i.test(value)) return null
  let normalized = value.replace(/,? or similar$/, '')
  const angle = /^ANGLE \((?:[^,]*), (.*?)(?:, [^,]*)?\)$/i.exec(normalized)
  if (angle?.[1]) normalized = angle[1].replace(/^ANGLE Metal Renderer: /i, '')
  normalized = normalized
    .replace(/\s*\(0x[0-9a-f]+\)/i, '')
    .replace(/\s+(Direct3D\d*|OpenGL|Vulkan|Metal)(\s.*)?$/i, '')
    .replace(/\s*\(TM\)|\(R\)/gi, '')
    .trim()
  return cleanValue(normalized, 160)
}

function resolveArchitecture(
  architecture: string | undefined,
  navigatorPlatform: string | undefined,
  platform: string | null,
  gpuRenderer: string | null,
) {
  const claimed = cleanValue(architecture, 24) || cleanValue(navigatorPlatform, 24)
  const armPlatform = /^(Android|iPadOS|iOS|HarmonyOS)$/.test(platform || '')
  const armGpu = /Maleoon|Mali|Adreno|Immortalis|Xclipse|PowerVR|Bifrost|Midgard/i.test(gpuRenderer || '')
  if (armPlatform && armGpu && /^(x86|x64|ia32|amd64|Win32|Win64|WOW64)$/i.test(claimed || '')) {
    return { value: 'ARM', claim: claimed }
  }
  return { value: claimed, claim: null }
}

function detectCapabilities(): DeviceCapabilities {
  const matches = (query: string) => {
    try {
      return typeof window.matchMedia === 'function' && window.matchMedia(query).matches
    } catch {
      return false
    }
  }
  const touch = Math.max(0, navigator.maxTouchPoints || 0) > 0
  let pointer: PointerType = 'unknown'
  if (matches('(pointer: none)')) pointer = 'none'
  else if (matches('(pointer: coarse)')) pointer = 'coarse'
  else if (matches('(pointer: fine)')) pointer = 'fine'
  else if (matches('(any-pointer: coarse)')) pointer = 'coarse'
  else if (matches('(any-pointer: fine)')) pointer = 'fine'

  const hover = matches('(hover: hover)')
    ? true
    : matches('(hover: none)') ? false : null
  return { pointer, hover, touch }
}

function resolvePlatform(
  nav: DeviceNavigator,
  userAgent: string,
  hints: UaHints,
  capabilities: DeviceCapabilities,
): PlatformResolution {
  const hintPlatform = normalizePlatform(hints.platform || nav.userAgentData?.platform || '')
  const uaPlatform = platformFromUserAgent(userAgent)
  const legacyPlatform = normalizePlatform(nav.platform || '')
  const touchPoints = Math.max(0, nav.maxTouchPoints || 0)
  const ipadDesktopMode = !/iPad/i.test(userAgent)
    && touchPoints > 1
    && (hintPlatform === 'macOS' || /Macintosh/i.test(userAgent) || legacyPlatform === 'macOS')

  if (/iPad/i.test(userAgent) || ipadDesktopMode) {
    return platformResult('iPadOS', iosVersion(userAgent, hints.platformVersion), 'high',
      ipadDesktopMode ? 'ipados_desktop_mode' : 'user_agent')
  }
  if (/iPhone|iPod/i.test(userAgent)) {
    return platformResult('iOS', iosVersion(userAgent, hints.platformVersion), 'high', 'user_agent')
  }
  if (hintPlatform === 'HarmonyOS' || uaPlatform === 'HarmonyOS') {
    return platformResult('HarmonyOS', harmonyVersion(userAgent, hints.platformVersion),
      hintPlatform ? 'high' : 'medium', hintPlatform ? 'client_hints' : 'user_agent')
  }
  if (hintPlatform === 'Android' || uaPlatform === 'Android') {
    return platformResult('Android', androidVersion(userAgent, hints.platformVersion),
      hintPlatform ? 'high' : 'medium', hintPlatform ? 'client_hints' : 'user_agent')
  }
  if (hintPlatform === 'Windows' || uaPlatform === 'Windows') {
    return platformResult('Windows', windowsVersion(userAgent, hints.platformVersion),
      hintPlatform ? 'high' : 'medium', hintPlatform ? 'client_hints' : 'user_agent')
  }
  if (hintPlatform === 'ChromeOS' || uaPlatform === 'ChromeOS') {
    return platformResult('ChromeOS', cleanValue(hints.platformVersion, 24),
      hintPlatform ? 'high' : 'medium', hintPlatform ? 'client_hints' : 'user_agent')
  }
  if (hintPlatform === 'macOS' || uaPlatform === 'macOS'
    || (legacyPlatform === 'macOS' && capabilities.pointer === 'fine')) {
    return platformResult('macOS', macVersion(userAgent, hints.platformVersion),
      hintPlatform ? 'high' : 'medium', hintPlatform ? 'client_hints' : 'user_agent')
  }
  if (hintPlatform === 'Linux' || uaPlatform === 'Linux') {
    return platformResult('Linux', cleanValue(hints.platformVersion, 24),
      hintPlatform ? 'high' : 'medium', hintPlatform ? 'client_hints' : 'user_agent')
  }

  const fallback = hintPlatform || legacyPlatform
  return platformResult(fallback, cleanValue(hints.platformVersion, 24),
    fallback ? 'low' : null, fallback ? 'legacy_probe' : 'unknown')
}

function platformResult(
  name: string | null,
  version: string | null,
  confidence: RecognitionConfidence | null,
  evidence: string,
): PlatformResolution {
  return { name, version, confidence, evidence }
}

function resolveDeviceType(
  nav: DeviceNavigator,
  userAgent: string,
  hints: UaHints,
  formFactors: string[],
  platform: PlatformResolution,
  capabilities: DeviceCapabilities,
): DeviceTypeResolution {
  const hintedType = ['tablet', 'mobile', 'desktop']
    .find((value) => formFactors.includes(value))
  if (hintedType) {
    return {
      type: hintedType as DeviceType,
      confidence: 'high',
      evidence: 'client_hints_form_factor',
    }
  }

  if (platform.name === 'iPadOS') {
    return { type: 'tablet', confidence: 'high', evidence: 'ipados' }
  }
  if (platform.name === 'iOS') {
    return { type: 'mobile', confidence: 'high', evidence: 'ios' }
  }
  if (platform.name === 'HarmonyOS') {
    if (/\(Phone;|Mobile/i.test(userAgent) || hints.mobile === true) {
      return { type: 'mobile', confidence: 'high', evidence: 'harmony_mobile' }
    }
    if (/\(Tablet;|Tablet/i.test(userAgent)) {
      return { type: 'tablet', confidence: 'high', evidence: 'harmony_tablet' }
    }
    return { type: 'unknown', confidence: 'low', evidence: 'harmony_unknown_form' }
  }
  if (platform.name === 'Android') {
    if (hints.mobile === true || /Mobile/i.test(userAgent)) {
      return { type: 'mobile', confidence: 'high', evidence: 'android_mobile' }
    }
    if (capabilities.touch) {
      return { type: 'tablet', confidence: 'high', evidence: 'android_tablet' }
    }
    return { type: 'unknown', confidence: 'low', evidence: 'android_without_touch' }
  }
  if (platform.name === 'Windows') {
    return { type: 'desktop', confidence: 'medium', evidence: 'windows_platform' }
  }
  if (platform.name === 'macOS') {
    return { type: 'desktop', confidence: 'medium', evidence: 'macos_platform' }
  }
  if (platform.name === 'ChromeOS') {
    return capabilities.touch && capabilities.pointer === 'coarse'
      ? { type: 'tablet', confidence: 'low', evidence: 'chromeos_touch' }
      : { type: 'desktop', confidence: 'medium', evidence: 'chromeos_platform' }
  }
  if (platform.name === 'Linux') {
    return capabilities.pointer === 'coarse'
      ? { type: 'unknown', confidence: 'low', evidence: 'linux_coarse_pointer' }
      : { type: 'desktop', confidence: 'medium', evidence: 'linux_platform' }
  }

  if (hints.mobile === true) {
    return { type: 'mobile', confidence: 'medium', evidence: 'client_hint_mobile' }
  }
  if (capabilities.touch && capabilities.pointer === 'coarse') {
    const shortSide = Math.min(window.screen?.width || 0, window.screen?.height || 0)
    return shortSide >= 600
      ? { type: 'tablet', confidence: 'low', evidence: 'touch_viewport' }
      : { type: 'mobile', confidence: 'low', evidence: 'touch_viewport' }
  }
  if (capabilities.pointer === 'fine' && capabilities.hover === true) {
    return { type: 'desktop', confidence: 'low', evidence: 'pointer_capability' }
  }
  return { type: 'unknown', confidence: 'low', evidence: 'insufficient_signals' }
}

function resolveBrowser(nav: DeviceNavigator, userAgent: string, hints: UaHints) {
  for (const [name, pattern, versionPattern] of inAppBrowsers) {
    if (pattern.test(userAgent)) {
      return { name, version: versionPattern ? matchVersion(userAgent, versionPattern) : null }
    }
  }

  if (nav.brave) {
    return { name: 'Brave', version: majorVersion(hints.uaFullVersion || hints.fullVersionList?.[0]?.version) }
  }
  if (/Edg(?:e|A|iOS)?\/([\d.]+)/i.test(userAgent)) {
    return { name: 'Edge', version: matchVersion(userAgent, /Edg(?:e|A|iOS)?\/([\d.]+)/i) }
  }
  if (/OPR\/([\d.]+)/i.test(userAgent)) {
    return { name: 'Opera', version: matchVersion(userAgent, /OPR\/([\d.]+)/i) }
  }
  if (/SamsungBrowser\/([\d.]+)/i.test(userAgent)) {
    return { name: 'Samsung Internet', version: matchVersion(userAgent, /SamsungBrowser\/([\d.]+)/i) }
  }
  if (/; wv\)/i.test(userAgent)) {
    return { name: 'Android WebView', version: matchVersion(userAgent, /Chrome\/([\d.]+)/i) }
  }

  const brands = (hints.fullVersionList?.length ? hints.fullVersionList : nav.userAgentData?.brands || [])
    .filter((item) => !/Not.?A.?Brand|Chromium/i.test(item.brand))
  const preferredBrands = [
    /Microsoft Edge/i,
    /Opera/i,
    /Google Chrome/i,
    /Samsung Internet/i,
  ]
  for (const pattern of preferredBrands) {
    const match = brands.find((item) => pattern.test(item.brand))
    if (match) {
      return {
        name: match.brand.replace(/^Microsoft /, '').replace(/^Google /, ''),
        version: cleanValue(match.version, 40),
      }
    }
  }

  if (/(?:Firefox|FxiOS)\/([\d.]+)/i.test(userAgent)) {
    return { name: 'Firefox', version: matchVersion(userAgent, /(?:Firefox|FxiOS)\/([\d.]+)/i) }
  }
  if (/(?:Chrome|CriOS)\/([\d.]+)/i.test(userAgent)) {
    return { name: 'Chrome', version: matchVersion(userAgent, /(?:Chrome|CriOS)\/([\d.]+)/i) }
  }
  if (/Version\/([\d.]+).*Safari/i.test(userAgent)) {
    const safari = /Version\/([\d.]+).*Safari/i.exec(userAgent)?.[1] || null
    return { name: 'Safari', version: safari }
  }
  if (/curl|httpie/i.test(userAgent)) return { name: '命令行客户端', version: null }

  const chromium = brands.find((item) => /Chromium/i.test(item.brand))
  if (chromium) return { name: 'Chromium', version: cleanValue(chromium.version, 40) }
  if (brands[0]) return { name: brands[0].brand, version: cleanValue(brands[0].version, 40) }
  return { name: '浏览器', version: null }
}

function matchVersion(value: string, pattern: RegExp): string | null {
  return cleanValue(pattern.exec(value)?.[1], 40)
}

function majorVersion(value?: string): string | null {
  return cleanValue(value?.split('.')[0], 16)
}

function normalizeFormFactors(values?: string[]): string[] {
  const known = new Set(['mobile', 'tablet', 'desktop', 'automotive', 'smartwatch', 'xr'])
  return [...new Set((values || [])
    .map((value) => value.trim().toLowerCase())
    .filter((value) => known.has(value)))]
}

function formFactorLabel(value: string): string {
  return {
    mobile: 'Mobile',
    tablet: 'Tablet',
    desktop: 'Desktop',
    automotive: 'Automotive',
    smartwatch: 'Smartwatch',
    xr: 'XR',
  }[value] || value
}

function normalizePlatform(value: string): string | null {
  const raw = value.trim().toLowerCase()
  if (!raw) return null
  if (raw.includes('harmony') || raw === 'openharmony') return 'HarmonyOS'
  if (raw.includes('android')) return 'Android'
  if (raw.includes('ipados') || raw.includes('ipad')) return 'iPadOS'
  if (raw === 'ios' || raw.includes('iphone') || raw.includes('ipod')) return 'iOS'
  if (raw.includes('windows') || raw === 'win32' || raw === 'win64') return 'Windows'
  if (raw.includes('mac') || raw === 'macintel') return 'macOS'
  if (raw.includes('chrome os') || raw.includes('chromeos') || raw === 'cros') return 'ChromeOS'
  if (raw.includes('linux') || raw.includes('x11')) return 'Linux'
  return null
}

function platformFromUserAgent(userAgent: string): string | null {
  if (/OpenHarmony|HarmonyOS|ArkWeb/i.test(userAgent)) return 'HarmonyOS'
  if (/Android/i.test(userAgent)) return 'Android'
  if (/iPhone|iPod/i.test(userAgent)) return 'iOS'
  if (/iPad/i.test(userAgent)) return 'iPadOS'
  if (/Windows NT/i.test(userAgent)) return 'Windows'
  if (/CrOS/i.test(userAgent)) return 'ChromeOS'
  if (/Macintosh|Mac OS X/i.test(userAgent)) return 'macOS'
  if (/Linux|X11/i.test(userAgent)) return 'Linux'
  return null
}

function windowsVersion(userAgent: string, platformVersion?: string): string | null {
  const version = cleanValue(platformVersion, 24)
  if (version) {
    const major = Number.parseInt(version.split('.')[0] || '', 10)
    if (Number.isFinite(major)) return major >= 13 ? '11' : '10/11'
  }
  const nt = /Windows NT ([\d.]+)/i.exec(userAgent)?.[1]
  return nt ? ({ '10.0': '10/11', '6.3': '8.1', '6.2': '8', '6.1': '7' }[nt] || nt) : null
}

function macVersion(userAgent: string, platformVersion?: string): string | null {
  const hinted = shortVersion(platformVersion, 2)
  if (hinted) return hinted
  const value = /Mac OS X ([\d_]+)/i.exec(userAgent)?.[1]
  return value ? value.replaceAll('_', '.') : null
}

function iosVersion(userAgent: string, platformVersion?: string): string | null {
  const hinted = shortVersion(platformVersion, 2)
  if (hinted) return hinted
  return /OS ([\d_]+) like Mac OS X/i.exec(userAgent)?.[1]?.replaceAll('_', '.') || null
}

function androidVersion(userAgent: string, platformVersion?: string): string | null {
  const hinted = shortVersion(platformVersion, 1)
  if (hinted) return hinted
  return /Android ([\d.]+)/i.exec(userAgent)?.[1] || null
}

function harmonyVersion(userAgent: string, platformVersion?: string): string | null {
  const hinted = shortVersion(platformVersion, 2)
  if (hinted) return hinted
  return /(?:OpenHarmony|HarmonyOS)[ /]?(\d+(?:\.\d+)?)/i.exec(userAgent)?.[1] || null
}

function shortVersion(value: string | undefined, parts: number): string | null {
  const normalized = cleanValue(value, 24)
  if (!normalized || normalized === '0.0.0') return null
  return normalized.split('.').slice(0, parts).join('.')
}

// Screen/version model tables adapted from yt1988/who-are-you (MIT).
type AppleModelRow = [number, number, number, Array<[string, number, number?]>]

const IPHONE_MODELS: AppleModelRow[] = [
  [320, 568, 2, [['iPhone SE (1st gen)', 9, 15], ['iPhone 5s', 7, 12]]],
  [375, 667, 2, [['iPhone SE (3rd gen)', 15], ['iPhone SE (2nd gen)', 13], ['iPhone 8', 11, 16], ['iPhone 7', 10, 15], ['iPhone 6s', 9, 15]]],
  [414, 736, 3, [['iPhone 8 Plus', 11, 16], ['iPhone 7 Plus', 10, 15], ['iPhone 6s Plus', 9, 15]]],
  [375, 812, 3, [['iPhone 13 mini', 15], ['iPhone 12 mini', 14], ['iPhone 11 Pro', 13], ['iPhone XS', 12, 18], ['iPhone X', 11, 16]]],
  [414, 896, 2, [['iPhone 11', 13], ['iPhone XR', 12, 18]]],
  [414, 896, 3, [['iPhone 11 Pro Max', 13], ['iPhone XS Max', 12, 18]]],
  [390, 844, 3, [['iPhone 16e', 18], ['iPhone 14', 16], ['iPhone 13 Pro', 15], ['iPhone 13', 15], ['iPhone 12 Pro', 14], ['iPhone 12', 14]]],
  [428, 926, 3, [['iPhone 14 Plus', 16], ['iPhone 13 Pro Max', 15], ['iPhone 12 Pro Max', 14]]],
  [393, 852, 3, [['iPhone 16', 18], ['iPhone 15 Pro', 17], ['iPhone 15', 17], ['iPhone 14 Pro', 16]]],
  [430, 932, 3, [['iPhone 16 Plus', 18], ['iPhone 15 Pro Max', 17], ['iPhone 15 Plus', 17], ['iPhone 14 Pro Max', 16]]],
  [402, 874, 3, [['iPhone 17 Pro', 26], ['iPhone 17', 26], ['iPhone 16 Pro', 18]]],
  [440, 956, 3, [['iPhone 17 Pro Max', 26], ['iPhone 16 Pro Max', 18]]],
  [420, 912, 3, [['iPhone Air', 26]]],
]

const IPAD_MODELS: AppleModelRow[] = [
  [768, 1024, 2, [['iPad mini (5th gen)', 12], ['iPad (6th gen)', 11, 17], ['iPad Air 2', 8, 15], ['iPad Pro 9.7', 9, 16]]],
  [744, 1133, 2, [['iPad mini (A17 Pro)', 18], ['iPad mini (6th gen)', 15]]],
  [810, 1080, 2, [['iPad (9th gen)', 15], ['iPad (8th gen)', 14], ['iPad (7th gen)', 13, 18]]],
  [834, 1112, 2, [['iPad Air (3rd gen)', 12], ['iPad Pro 10.5', 10, 17]]],
  [820, 1180, 2, [['iPad (A16)', 18], ['iPad Air 11 (M2/M3)', 17], ['iPad (10th gen)', 16], ['iPad Air (5th gen)', 15], ['iPad Air (4th gen)', 14]]],
  [834, 1194, 2, [['iPad Pro 11', 12]]],
  [834, 1210, 2, [['iPad Pro 11 (M4/M5)', 17]]],
  [1024, 1366, 2, [['iPad Air 13 (M2/M3)', 17], ['iPad Pro 12.9', 9]]],
  [1032, 1376, 2, [['iPad Pro 13 (M4/M5)', 17]]],
]

function resolveModel(userAgent: string, hints: UaHints, platform: string | null): ModelResolution {
  const hinted = cleanValue(hints.model, 80)
  if (hinted && hinted !== 'K') return { name: hinted, source: 'client_hint' }
  if (platform === 'iPadOS' || platform === 'iOS') {
    const mapped = appleModel(platform, userAgent, hints.platformVersion)
    if (mapped) return { name: mapped, source: 'screen_mapping' }
    return { name: platform === 'iPadOS' ? 'iPad' : 'iPhone', source: 'generic_os' }
  }
  if (platform === 'HarmonyOS') {
    return {
      name: cleanValue(/HarmonyOS; ([^;)]+?)[;)]/i.exec(userAgent)?.[1], 80),
      source: 'user_agent',
    }
  }
  if (platform === 'Android') {
    return {
      name: cleanValue(/Android[^;]*;\s*([^;)]+?)(?:\s+Build|\))/i.exec(userAgent)?.[1], 80),
      source: 'user_agent',
    }
  }
  return { name: null, source: null }
}

function appleModel(platform: string, userAgent: string, platformVersion?: string): string | null {
  const width = window.screen?.width || 0
  const height = window.screen?.height || 0
  if (!width || !height) return null
  const shortSide = Math.min(width, height)
  const longSide = Math.max(width, height)
  const ratio = Math.round(window.devicePixelRatio || 1)
  const version = appleMajorVersion(userAgent, platformVersion)
  const rows = platform === 'iPadOS' ? IPAD_MODELS : IPHONE_MODELS
  const row = rows.find(([rowWidth, rowHeight, rowRatio]) =>
    rowWidth === shortSide && rowHeight === longSide && rowRatio === ratio)
  if (!row) return null
  let candidates = row[3]
    .filter(([, from, until]) => !version || (version >= from && (!until || version <= until)))
    .map(([name]) => name)
  if (!candidates.length) candidates = row[3].map(([name]) => name)
  if (!candidates.length) return null
  const label = candidates.length === 1
    ? candidates[0]
    : candidates.slice(0, 3).join(' / ') + (candidates.length > 3 ? ' 等' : '')
  return cleanValue(label, 80)
}

function appleMajorVersion(userAgent: string, platformVersion?: string): number | null {
  const hinted = Number.parseInt(platformVersion || '', 10)
  if (Number.isFinite(hinted) && hinted > 0) return hinted
  const os = Number.parseInt(/OS (\d+)[_.]/i.exec(userAgent)?.[1] || '', 10)
  const browser = Number.parseInt(/Version\/(\d+)/i.exec(userAgent)?.[1] || '', 10)
  if (Number.isFinite(browser) && browser >= 26) return Math.max(os || 0, browser)
  return Number.isFinite(os) && os > 0 ? os : null
}

function screenDescription(): string | null {
  const width = window.screen?.width || 0
  const height = window.screen?.height || 0
  if (!width || !height) return null
  const ratio = window.devicePixelRatio || 1
  return `${Math.round(width * ratio)}×${Math.round(height * ratio)} px`
}

function screenCssDescription(): string | null {
  const width = window.screen?.width || 0
  const height = window.screen?.height || 0
  return width && height ? `${width}×${height}` : null
}

function screenAvailableDescription(): string | null {
  const width = window.screen?.availWidth || 0
  const height = window.screen?.availHeight || 0
  return width && height ? `${width}×${height}` : null
}

function viewportDescription(): string | null {
  const width = window.innerWidth || 0
  const height = window.innerHeight || 0
  return width && height ? `${width}×${height}` : null
}

function screenOrientation(): string | null {
  const type = window.screen?.orientation?.type
  if (type) return type
  const width = window.screen?.width || 0
  const height = window.screen?.height || 0
  if (!width || !height) return null
  return width > height ? 'landscape' : 'portrait'
}

function displayMode(): string | null {
  const modes = ['fullscreen', 'standalone', 'minimal-ui', 'window-controls-overlay', 'browser']
  for (const mode of modes) {
    try {
      if (window.matchMedia?.(`(display-mode: ${mode})`).matches) return mode
    } catch {
      return null
    }
  }
  return null
}

function finiteNumber(value: number | undefined): number | null {
  return typeof value === 'number' && Number.isFinite(value) && value > 0 ? value : null
}

function nonNegativeNumber(value: number | undefined, integer = false): number | null {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0) return null
  return integer ? Math.round(value) : value
}

function cleanValue(value: string | undefined, maxLength: number): string | null {
  const normalized = value?.replace(/[\u0000-\u001f\u007f]/g, '').trim()
  if (!normalized) return null
  return normalized.length <= maxLength ? normalized : normalized.substring(0, maxLength)
}

function hasClientHints(hints: UaHints): boolean {
  return Boolean(hints.platform || hints.platformVersion || hints.model
    || hints.architecture || hints.formFactors?.length)
}
