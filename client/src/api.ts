export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) { super(message); this.status = status }
}
let csrf: { token: string; headerName: string } | undefined
export function clearCsrf() { csrf = undefined }
export async function refreshCsrf() {
  const response = await fetch('/api/v1/auth/csrf', { credentials: 'same-origin', cache: 'no-store' })
  if (!response.ok) throw new ApiError(response.status, 'Could not establish a secure session. Please retry.')
  csrf = await response.json()
  return csrf!
}
export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const method = options.method || 'GET'
  const headers = new Headers(options.headers)
  if (options.body) headers.set('Content-Type', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const token = csrf || await refreshCsrf()
    headers.set(token.headerName, token.token)
  }
  const response = await fetch(`/api/v1${path}`, { ...options, headers, credentials: 'same-origin', cache: 'no-store' })
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}))
    if (response.status === 401) { clearCsrf(); window.dispatchEvent(new Event('session-expired')) }
    if (response.status === 403) csrf = undefined
    throw new ApiError(response.status, problem.detail || problem.message || (response.status === 403 ? 'You do not have permission to perform this action.' : 'The request could not be completed. Please retry.'))
  }
  if (response.status === 204) return undefined as T
  return response.json()
}
export const post = <T,>(path: string, body?: unknown) => api<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) })
export const patch = <T,>(path: string, body: unknown) => api<T>(path, { method: 'PATCH', body: JSON.stringify(body) })
export function safeReturnPath(path: string | null) {
  if (!path || !path.startsWith('/') || path.startsWith('//') || path.includes('\\') || [...path].some(character => character.charCodeAt(0) <= 32) || path.startsWith('/login')) return '/dashboard'
  try { return new URL(path, window.location.origin).origin === window.location.origin ? path : '/dashboard' }
  catch { return '/dashboard' }
}
