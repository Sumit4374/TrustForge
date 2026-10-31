import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { api, ApiError, clearCsrf, post, refreshCsrf } from './api'
import type { User } from './types'
import { AuthContext, useAuth } from './session'
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const client = useQueryClient()
  const reload = useCallback(async (silent = false) => {
    if (!silent) setLoading(true); setError('')
    try { setUser(await api<User>('/auth/me')) }
    catch (e) { setUser(null); if (!(e instanceof ApiError && e.status === 401)) setError(e instanceof Error ? e.message : 'Session unavailable') }
    finally { if (!silent) setLoading(false) }
  }, [])
  useEffect(() => { void Promise.resolve().then(() => reload()) }, [reload])
  useEffect(() => {
    const expired = () => { clearCsrf(); setUser(null); client.clear() }
    window.addEventListener('session-expired', expired)
    return () => window.removeEventListener('session-expired', expired)
  }, [client])
  const login = async (email: string, password: string) => { client.clear(); setUser(await post<User>('/auth/login', { email, password })); await refreshCsrf() }
  const logout = async () => { await post('/auth/logout'); setUser(null); client.clear(); await refreshCsrf() }
  return <AuthContext value={{ user, loading, error, reload, login, logout }}>{children}</AuthContext>
}
export function ProtectedRoute() {
  const { user, loading, error, reload } = useAuth()
  const location = useLocation()
  if (loading) return <div className="full-state"><span className="spinner" /> Establishing your session…</div>
  if (error) return <div className="full-state"><p>{error}</p><button onClick={() => void reload()}>Retry connection</button></div>
  return user ? <Outlet /> : <Navigate to={`/login?returnTo=${encodeURIComponent(location.pathname + location.search)}`} replace />
}
