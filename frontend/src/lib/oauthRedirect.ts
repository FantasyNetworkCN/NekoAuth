export function stripFreshAuthParams(target: string): string {
  if (!target.startsWith('/') || target.startsWith('//')) return target
  try {
    const url = new URL(target, window.location.origin)
    const remainingPrompt = (url.searchParams.get('prompt') || '')
      .split(/\s+/)
      .filter((value) => value && value !== 'login' && value !== 'select_account')
      .join(' ')
    if (remainingPrompt) url.searchParams.set('prompt', remainingPrompt)
    else url.searchParams.delete('prompt')
    url.searchParams.delete('max_age')
    return `${url.pathname}${url.search}${url.hash}`
  } catch {
    return target
  }
}
