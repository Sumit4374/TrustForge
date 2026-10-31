import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { KeyRound, LockKeyhole } from 'lucide-react'
import { api, patch, post } from './api'
import { date } from './format'
import { useAuth } from './session'
import { Card, ErrorBox, Field, Loading, PageHead, Submit } from './ui'

type Session = { handle: string; signedInAt: string; lastAccessedAt: string; expiresAt: string; current: boolean }

export function SettingsPage() {
  const { user, reload } = useAuth()
  const client = useQueryClient()
  const sessions = useQuery({ queryKey: ['account-sessions'], queryFn: () => api<Session[]>('/auth/sessions'), refetchInterval: 30_000 })
  const [error, setError] = useState<unknown>()
  const [profileError, setProfileError] = useState<unknown>()
  const [sessionError, setSessionError] = useState<unknown>()
  const [busy, setBusy] = useState(false)
  const [saving, setSaving] = useState(false)
  const [revoking, setRevoking] = useState('')
  const [saved, setSaved] = useState(false)
  const changePassword = async (data: FormData) => {
    setBusy(true); setError(undefined)
    try { await post('/auth/change-password', { currentPassword: data.get('currentPassword'), password: data.get('password') }); window.dispatchEvent(new Event('session-expired')) }
    catch (e) { setError(e) } finally { setBusy(false) }
  }
  const saveProfile = async (data: FormData) => {
    setSaving(true); setProfileError(undefined); setSaved(false)
    try { await patch('/auth/me', { name: data.get('name') }); await reload(true); setSaved(true) }
    catch (e) { setProfileError(e) } finally { setSaving(false) }
  }
  const revoke = async (handle: string) => {
    setRevoking(handle); setSessionError(undefined)
    try { await api(`/auth/sessions/${handle}`, { method: 'DELETE' }); await client.invalidateQueries({ queryKey: ['account-sessions'] }) }
    catch (e) { setSessionError(e) } finally { setRevoking('') }
  }
  return <><PageHead eyebrow="YOUR ACCOUNT" title="Settings" description="Manage your identity and keep your workspace access secure." />
    <div className="configuration-grid">
      <Card className="form-card"><h3>Account details</h3>
        <form onSubmit={e => { e.preventDefault(); void saveProfile(new FormData(e.currentTarget)) }}>
          <Field label="Name"><input name="name" defaultValue={user?.name || ''} autoComplete="name" maxLength={100} required /></Field>
          <Field label="Email"><input readOnly value={user?.email || ''} /></Field>
          <ErrorBox error={profileError} />{saved && <p role="status">Profile saved.</p>}<Submit pending={saving}>Save profile</Submit>
        </form>
        <p className="muted">Your platform role: <span className="mono">{user?.role}</span>. Project permissions are managed by each project owner.</p>
        <div className="info-box"><LockKeyhole size={19} /><p>Authentication uses an HttpOnly cookie and server-side sessions. No credentials are persisted in browser storage.</p></div>
      </Card>
      <Card className="form-card"><h3>Change password</h3><p className="muted">Changing your password revokes existing sessions. You will need to sign in again.</p>
        <form onSubmit={e => { e.preventDefault(); void changePassword(new FormData(e.currentTarget)) }}>
          <Field label="Current password"><input name="currentPassword" type="password" autoComplete="current-password" maxLength={128} required /></Field>
          <Field label="New password" hint="Use a unique password with at least 12 characters."><input name="password" type="password" autoComplete="new-password" minLength={12} maxLength={128} required /></Field>
          <ErrorBox error={error} /><Submit pending={busy}><KeyRound size={16} />Change password and sign out</Submit>
        </form>
      </Card>
    </div>
    <Card className="form-card"><h3>Active sessions</h3><p className="muted">End access from another session. Use Sign out in the navigation to end this session. Times reflect server activity.</p>
      <ErrorBox error={sessions.error} retry={() => void sessions.refetch()} /><ErrorBox error={sessionError} />
      {sessions.isPending ? <Loading text="Loading your active sessions…" /> : sessions.data?.map((session, index) => <div className="toolbar" key={session.handle}>
        <div><strong>{session.current ? 'Current session' : `Other session ${index}`}</strong><p className="muted">Signed in {date(session.signedInAt)} · Last active {date(session.lastAccessedAt)} · Expires {date(session.expiresAt)}</p></div>
        {!session.current && <button className="button secondary" disabled={!!revoking} onClick={() => void revoke(session.handle)}>{revoking === session.handle ? 'Ending session…' : 'End session'}</button>}
      </div>)}
    </Card>
  </>
}
